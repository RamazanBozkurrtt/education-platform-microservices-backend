package com.edubase.course.recommendation.service;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.recommendation.config.RecommendationServiceProperties;
import com.edubase.course.recommendation.model.UserRecommendationProfile;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.repository.CourseLevelRepository;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationProfileService {

    private final LessonProgressRepository lessonProgressRepository;
    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseLevelRepository courseLevelRepository;
    private final RecommendationServiceProperties properties;

    @Value("${course.progress.completion-threshold-percentage:90}")
    private BigDecimal completionThresholdPercentage;

    public UserRecommendationProfile buildProfile(String userId) {
        if (!hasText(userId)) {
            return coldStartProfile();
        }

        List<LessonProgress> progresses = lessonProgressRepository.findByUserIdOrderByUpdatedAtDesc(userId.trim());
        if (progresses.isEmpty()) {
            return coldStartProfile();
        }

        Map<String, Course> coursesById = resolvePublishedCoursesById(progresses);
        if (coursesById.isEmpty()) {
            return coldStartProfile();
        }

        Map<String, List<LessonProgress>> progressesByCourse = progresses.stream()
                .filter(progress -> hasText(progress.getCourseId()))
                .filter(progress -> coursesById.containsKey(progress.getCourseId()))
                .collect(Collectors.groupingBy(LessonProgress::getCourseId));

        if (progressesByCourse.isEmpty()) {
            return coldStartProfile();
        }

        BigDecimal completionThreshold = effectiveCompletionThreshold();
        Map<String, BigDecimal> completionByCourse = new HashMap<>();
        for (Map.Entry<String, List<LessonProgress>> entry : progressesByCourse.entrySet()) {
            BigDecimal completion = calculateAverageCompletion(entry.getValue());
            completionByCourse.put(entry.getKey(), completion);
        }

        List<String> startedCourseIds = completionByCourse.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .toList();

        List<String> completedCourseIds = completionByCourse.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(completionThreshold) >= 0)
                .map(Map.Entry::getKey)
                .toList();

        List<String> inProgressCourseIds = completionByCourse.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .filter(entry -> entry.getValue().compareTo(completionThreshold) < 0)
                .map(Map.Entry::getKey)
                .toList();

        BigDecimal averageCompletionRate = completionByCourse.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!completionByCourse.isEmpty()) {
            averageCompletionRate = averageCompletionRate.divide(
                    BigDecimal.valueOf(completionByCourse.size()),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        long dropoutCount = completionByCourse.values().stream()
                .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                .filter(value -> value.doubleValue() < properties.getDropoutLowProgressThresholdPercent())
                .count();
        double dropoutRate = startedCourseIds.isEmpty()
                ? 0.0d
                : roundTo2((dropoutCount * 100.0d) / startedCourseIds.size());

        Map<String, Long> categoryFrequency = new HashMap<>();
        Map<String, Long> levelFrequency = new HashMap<>();
        Map<String, String> categoryNamesById = new HashMap<>();
        Map<String, String> levelNamesById = new HashMap<>();

        Set<String> allCategoryIds = new HashSet<>();
        Set<String> allLevelIds = new HashSet<>();
        for (String courseId : startedCourseIds) {
            Course course = coursesById.get(courseId);
            if (course == null) {
                continue;
            }
            allCategoryIds.addAll(resolveCategoryIds(course));
            if (hasText(course.getLevelId())) {
                allLevelIds.add(course.getLevelId().trim());
            }
        }

        categoryNamesById.putAll(categoryRepository.findAllById(allCategoryIds).stream()
                .collect(Collectors.toMap(category -> category.getId(), category -> category.getCategoryName(), (a, b) -> a)));
        levelNamesById.putAll(courseLevelRepository.findAllById(allLevelIds).stream()
                .collect(Collectors.toMap(level -> level.getId(), level -> level.getLevelName(), (a, b) -> a)));

        for (String courseId : startedCourseIds) {
            Course course = coursesById.get(courseId);
            if (course == null) {
                continue;
            }

            for (String categoryId : resolveCategoryIds(course)) {
                String categoryName = categoryNamesById.get(categoryId);
                if (hasText(categoryName)) {
                    categoryFrequency.merge(categoryName.trim(), 1L, Long::sum);
                }
            }

            String levelName = levelNamesById.get(course.getLevelId());
            if (hasText(levelName)) {
                levelFrequency.merge(levelName.trim(), 1L, Long::sum);
            }
        }

        List<String> favoriteCategories = categoryFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        List<String> preferredLevels = levelFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        long preferredDurationSeconds = calculatePreferredDurationSeconds(completedCourseIds, coursesById);

        List<String> recentlyWatchedCourseIds = progresses.stream()
                .map(LessonProgress::getCourseId)
                .filter(coursesById::containsKey)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(Math.max(1, properties.getRecentCourseLimit()))
                .toList();

        return UserRecommendationProfile.builder()
                .favoriteCategories(favoriteCategories)
                .averageCompletionRate(averageCompletionRate.doubleValue())
                .dropoutRate(dropoutRate)
                .preferredDurationSeconds(preferredDurationSeconds)
                .completedCourseIds(completedCourseIds)
                .inProgressCourseIds(inProgressCourseIds)
                .recentlyWatchedCourseIds(recentlyWatchedCourseIds)
                .preferredLevels(preferredLevels)
                .coldStart(false)
                .build();
    }

    public String resolveDropoutRisk(double dropoutRate) {
        if (dropoutRate >= 60.0d) {
            return "HIGH";
        }
        if (dropoutRate >= 30.0d) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public String resolvePreferredDurationLabel(long preferredDurationSeconds) {
        if (preferredDurationSeconds <= 3600L) {
            return "SHORT";
        }
        if (preferredDurationSeconds <= 10800L) {
            return "MEDIUM";
        }
        return "LONG";
    }

    private UserRecommendationProfile coldStartProfile() {
        return UserRecommendationProfile.builder()
                .favoriteCategories(List.of())
                .averageCompletionRate(0.0d)
                .dropoutRate(0.0d)
                .preferredDurationSeconds((long) properties.getDefaultPreferredDurationSeconds())
                .completedCourseIds(List.of())
                .inProgressCourseIds(List.of())
                .recentlyWatchedCourseIds(List.of())
                .preferredLevels(List.of())
                .coldStart(true)
                .build();
    }

    private Map<String, Course> resolvePublishedCoursesById(List<LessonProgress> progresses) {
        Set<String> courseIds = progresses.stream()
                .map(LessonProgress::getCourseId)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return Map.of();
        }

        return courseRepository.findAllByIdInAndStatusAndDeletedAtIsNull(courseIds, CourseStatus.PUBLISHED).stream()
                .collect(Collectors.toMap(Course::getId, course -> course, (a, b) -> a));
    }

    private BigDecimal calculateAverageCompletion(List<LessonProgress> progresses) {
        if (progresses == null || progresses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = progresses.stream()
                .map(progress -> progress.getWatchedPercentage() == null ? BigDecimal.ZERO : progress.getWatchedPercentage())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(progresses.size()), 2, RoundingMode.HALF_UP);
    }

    private long calculatePreferredDurationSeconds(List<String> completedCourseIds, Map<String, Course> coursesById) {
        if (completedCourseIds == null || completedCourseIds.isEmpty()) {
            return properties.getDefaultPreferredDurationSeconds();
        }
        List<Long> durations = new ArrayList<>();
        for (String courseId : completedCourseIds) {
            Course course = coursesById.get(courseId);
            if (course == null) {
                continue;
            }
            long duration = calculateDurationSeconds(course);
            if (duration > 0) {
                durations.add(duration);
            }
        }
        if (durations.isEmpty()) {
            return properties.getDefaultPreferredDurationSeconds();
        }
        long sum = durations.stream().reduce(0L, Long::sum);
        return sum / durations.size();
    }

    private long calculateDurationSeconds(Course course) {
        if (course == null || course.getLessons() == null || course.getLessons().isEmpty()) {
            return 0L;
        }
        return course.getLessons().stream()
                .map(Lesson::getDuration)
                .filter(duration -> duration != null && duration > 0)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private BigDecimal effectiveCompletionThreshold() {
        if (completionThresholdPercentage == null) {
            return BigDecimal.valueOf(90);
        }
        if (completionThresholdPercentage.compareTo(BigDecimal.ZERO) <= 0
                || completionThresholdPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(90);
        }
        return completionThresholdPercentage;
    }

    @SuppressWarnings("deprecation")
    private List<String> resolveCategoryIds(Course course) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (course.getCategoryIds() != null) {
            course.getCategoryIds().stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .forEach(ids::add);
        }
        if (ids.isEmpty() && hasText(course.getCategoryId())) {
            ids.add(course.getCategoryId().trim());
        }
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private double roundTo2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
