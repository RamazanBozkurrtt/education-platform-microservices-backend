package com.edubase.course.recommendation.service;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.recommendation.config.RecommendationServiceProperties;
import com.edubase.course.recommendation.model.CandidateCourseData;
import com.edubase.course.recommendation.model.UserRecommendationProfile;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.repository.CourseLevelRepository;
import com.edubase.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateCourseService {

    private static final String DEFAULT_THUMBNAIL_TEMPLATE = "/courses/public/%s/image";

    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final CourseLevelRepository courseLevelRepository;
    private final RecommendationServiceProperties properties;

    public List<CandidateCourseData> buildCandidates(UserRecommendationProfile profile, String query) {
        int candidateLimit = Math.max(1, properties.getCandidateLimit());
        int fetchSize = Math.min(500, Math.max(candidateLimit * 3, candidateLimit));
        PageRequest pageRequest = PageRequest.of(0, fetchSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Course> courses = courseRepository.findAllByStatusAndDeletedAtIsNull(CourseStatus.PUBLISHED, pageRequest)
                .getContent();

        if (courses.isEmpty()) {
            return List.of();
        }

        Map<String, String> categoryNamesById = resolveCategoryNames(courses);
        Map<String, String> levelNamesById = resolveLevelNames(courses);
        Set<String> completedCourseIds = new HashSet<>(safeList(profile.getCompletedCourseIds()));

        List<CandidateCourseData> candidates = new ArrayList<>();
        for (Course course : courses) {
            if (course == null || !hasText(course.getId()) || completedCourseIds.contains(course.getId())) {
                continue;
            }

            String category = resolvePrimaryCategoryName(course, categoryNamesById);
            String level = resolveLevelName(course, levelNamesById);

            candidates.add(CandidateCourseData.builder()
                    .courseId(course.getId())
                    .title(defaultString(course.getTitle()))
                    .description(defaultString(course.getDescription()))
                    .category(category)
                    .level(level)
                    .tags(sanitizeTags(course.getTags()))
                    .durationSeconds(calculateDurationSeconds(course))
                    .lessonCount(countLessons(course))
                    .rating(0.0d)
                    .enrollmentCount(0L)
                    .createdAt(course.getCreatedAt())
                    .thumbnailUrl(DEFAULT_THUMBNAIL_TEMPLATE.formatted(course.getId()))
                    .build());
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<CandidateCourseData> filtered = filterBySearchQuery(candidates, query);
        if (filtered.isEmpty()) {
            filtered = candidates;
        }

        if (profile != null && !profile.isColdStart()) {
            return filtered.stream()
                    .sorted(Comparator.comparingDouble(candidate -> -profileScore(candidate, profile)))
                    .limit(candidateLimit)
                    .toList();
        }

        return filtered.stream()
                .sorted(Comparator.comparingDouble(candidate -> -coldStartScore(candidate)))
                .limit(candidateLimit)
                .toList();
    }

    private Map<String, String> resolveCategoryNames(List<Course> courses) {
        Set<String> categoryIds = new HashSet<>();
        for (Course course : courses) {
            categoryIds.addAll(resolveCategoryIds(course));
        }
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(category -> category.getId(), category -> category.getCategoryName(), (a, b) -> a));
    }

    private Map<String, String> resolveLevelNames(List<Course> courses) {
        Set<String> levelIds = courses.stream()
                .map(Course::getLevelId)
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        if (levelIds.isEmpty()) {
            return Map.of();
        }
        return courseLevelRepository.findAllById(levelIds).stream()
                .collect(Collectors.toMap(level -> level.getId(), level -> level.getLevelName(), (a, b) -> a));
    }

    private List<CandidateCourseData> filterBySearchQuery(List<CandidateCourseData> candidates, String query) {
        if (!hasText(query)) {
            return candidates;
        }
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> containsToken(candidate, normalizedQuery))
                .toList();
    }

    private boolean containsToken(CandidateCourseData candidate, String normalizedQuery) {
        if (candidate == null) {
            return false;
        }
        if (defaultString(candidate.getTitle()).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (defaultString(candidate.getDescription()).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (defaultString(candidate.getCategory()).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (defaultString(candidate.getLevel()).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        return safeList(candidate.getTags()).stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(tag -> tag.contains(normalizedQuery));
    }

    private double profileScore(CandidateCourseData candidate, UserRecommendationProfile profile) {
        double score = 0.0d;
        Set<String> favoriteCategories = safeLowerSet(profile.getFavoriteCategories());
        Set<String> preferredLevels = safeLowerSet(profile.getPreferredLevels());
        Set<String> recentCourseIds = new HashSet<>(safeList(profile.getRecentlyWatchedCourseIds()));

        if (hasText(candidate.getCategory()) && favoriteCategories.contains(candidate.getCategory().toLowerCase(Locale.ROOT))) {
            score += 60.0d;
        }
        if (hasText(candidate.getLevel()) && preferredLevels.contains(candidate.getLevel().toLowerCase(Locale.ROOT))) {
            score += 30.0d;
        }
        if (recentCourseIds.contains(candidate.getCourseId())) {
            score += 10.0d;
        }
        score += Math.max(0, 15 - Math.abs(candidate.getDurationSeconds() - profile.getPreferredDurationSeconds()) / 600.0d);
        score += recencyBoost(candidate.getCreatedAt());
        return score;
    }

    private double coldStartScore(CandidateCourseData candidate) {
        double score = recencyBoost(candidate.getCreatedAt());
        if (hasText(candidate.getLevel())) {
            String normalized = candidate.getLevel().toLowerCase(Locale.ROOT);
            if (normalized.contains("beginner") || normalized.contains("basic")) {
                score += 40.0d;
            }
        }
        if (candidate.getDurationSeconds() != null && candidate.getDurationSeconds() <= 3600L) {
            score += 15.0d;
        }
        if (candidate.getLessonCount() != null && candidate.getLessonCount() >= 4 && candidate.getLessonCount() <= 20) {
            score += 10.0d;
        }
        return score;
    }

    private double recencyBoost(Instant createdAt) {
        if (createdAt == null) {
            return 0.0d;
        }
        long ageDays = Math.max(0, (Instant.now().toEpochMilli() - createdAt.toEpochMilli()) / (1000L * 60 * 60 * 24));
        return Math.max(0.0d, 20.0d - ageDays / 3.0d);
    }

    private Set<String> safeLowerSet(List<String> values) {
        return safeList(values).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
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
        return new ArrayList<>(ids);
    }

    private String resolvePrimaryCategoryName(Course course, Map<String, String> categoryNamesById) {
        for (String categoryId : resolveCategoryIds(course)) {
            String categoryName = categoryNamesById.get(categoryId);
            if (hasText(categoryName)) {
                return categoryName.trim();
            }
        }
        return "General";
    }

    private String resolveLevelName(Course course, Map<String, String> levelNamesById) {
        if (!hasText(course.getLevelId())) {
            return "Unknown";
        }
        String level = levelNamesById.get(course.getLevelId().trim());
        return hasText(level) ? level.trim() : "Unknown";
    }

    private long calculateDurationSeconds(Course course) {
        if (course.getLessons() == null || course.getLessons().isEmpty()) {
            return 0L;
        }
        return course.getLessons().stream()
                .map(Lesson::getDuration)
                .filter(duration -> duration != null && duration > 0)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private int countLessons(Course course) {
        if (course.getLessons() == null) {
            return 0;
        }
        int count = 0;
        for (Lesson lesson : course.getLessons()) {
            if (lesson != null) {
                count++;
            }
        }
        return count;
    }

    private List<String> sanitizeTags(List<String> tags) {
        return safeList(tags).stream()
                .filter(this::hasText)
                .map(String::trim)
                .limit(10)
                .toList();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
