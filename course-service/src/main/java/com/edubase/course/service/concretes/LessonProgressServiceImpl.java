package com.edubase.course.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.dto.request.LessonProgressUpdateRequest;
import com.edubase.course.dto.response.CourseProgressSummaryResponse;
import com.edubase.course.dto.response.LessonProgressResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.LessonNotFoundException;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.repository.LessonProgressRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.LessonProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LessonProgressServiceImpl implements LessonProgressService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_COMPLETION_THRESHOLD = BigDecimal.valueOf(90);

    private final CourseRepository courseRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentAccessClient enrollmentAccessClient;

    @Value("${course.progress.completion-threshold-percentage:90}")
    private BigDecimal completionThresholdPercentage;

    @Override
    @Transactional
    public LessonProgressResponse updateLessonProgress(
            AuthContext authContext,
            String courseId,
            String lessonId,
            LessonProgressUpdateRequest request) {
        validateRequest(request);

        Course course = resolveCourseForProgress(authContext, courseId);
        resolveLesson(course, lessonId);
        String userId = requireUserId(authContext);

        int clampedRequestedSecond = Math.min(request.getLastWatchedSecond(), request.getVideoDurationSecond());
        Optional<LessonProgress> existingOptional =
                lessonProgressRepository.findByUserIdAndCourseIdAndLessonId(userId, course.getId(), lessonId);

        LessonProgress progress = existingOptional.orElseGet(() -> LessonProgress.builder()
                .userId(userId)
                .courseId(course.getId())
                .lessonId(lessonId)
                .lastWatchedSecond(0)
                .watchedPercentage(ZERO)
                .completed(false)
                .build());

        applyProgressRules(progress, clampedRequestedSecond, request.getVideoDurationSecond());

        try {
            LessonProgress saved = lessonProgressRepository.save(progress);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            LessonProgress existing = lessonProgressRepository
                    .findByUserIdAndCourseIdAndLessonId(userId, course.getId(), lessonId)
                    .orElseThrow(() -> ex);
            applyProgressRules(existing, clampedRequestedSecond, request.getVideoDurationSecond());
            LessonProgress saved = lessonProgressRepository.save(existing);
            return toResponse(saved);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LessonProgressResponse getLessonProgress(AuthContext authContext, String courseId, String lessonId) {
        Course course = resolveCourseForProgress(authContext, courseId);
        resolveLesson(course, lessonId);
        String userId = requireUserId(authContext);

        return lessonProgressRepository.findByUserIdAndCourseIdAndLessonId(userId, course.getId(), lessonId)
                .map(this::toResponse)
                .orElseGet(() -> LessonProgressResponse.builder()
                        .courseId(course.getId())
                        .lessonId(lessonId)
                        .lastWatchedSecond(0)
                        .watchedPercentage(ZERO.setScale(2, RoundingMode.HALF_UP))
                        .completed(false)
                        .completedAt(null)
                        .updatedAt(null)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonProgressResponse> getCourseLessonProgresses(AuthContext authContext, String courseId) {
        Course course = resolveCourseForProgress(authContext, courseId);
        String userId = requireUserId(authContext);
        Set<String> lessonIds = lessonIdsOf(course);

        return lessonProgressRepository.findByUserIdAndCourseIdOrderByUpdatedAtDesc(userId, course.getId()).stream()
                .filter(progress -> lessonIds.contains(progress.getLessonId()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseProgressSummaryResponse getCourseProgressSummary(AuthContext authContext, String courseId) {
        Course course = resolveCourseForProgress(authContext, courseId);
        String userId = requireUserId(authContext);
        Set<String> lessonIds = lessonIdsOf(course);

        List<LessonProgress> progresses = lessonProgressRepository.findByUserIdAndCourseId(userId, course.getId()).stream()
                .filter(progress -> lessonIds.contains(progress.getLessonId()))
                .toList();

        int totalLessons = lessonIds.size();
        int completedLessons = (int) progresses.stream()
                .filter(LessonProgress::isCompleted)
                .count();

        BigDecimal sumPercentages = progresses.stream()
                .map(item -> item.getWatchedPercentage() == null ? ZERO : item.getWatchedPercentage())
                .reduce(ZERO, BigDecimal::add);

        BigDecimal overallPercentage = totalLessons == 0
                ? ZERO.setScale(2, RoundingMode.HALF_UP)
                : sumPercentages.divide(BigDecimal.valueOf(totalLessons), 2, RoundingMode.HALF_UP);

        LessonProgress lastActivity = progresses.stream()
                .filter(item -> item.getUpdatedAt() != null)
                .max(Comparator.comparing(LessonProgress::getUpdatedAt))
                .orElse(null);

        return CourseProgressSummaryResponse.builder()
                .courseId(course.getId())
                .totalLessons(totalLessons)
                .completedLessons(completedLessons)
                .overallPercentage(overallPercentage)
                .lastLessonId(lastActivity == null ? null : lastActivity.getLessonId())
                .lastWatchedSecond(lastActivity == null ? null : lastActivity.getLastWatchedSecond())
                .lastActivityAt(lastActivity == null ? null : lastActivity.getUpdatedAt())
                .build();
    }

    private void validateRequest(LessonProgressUpdateRequest request) {
        if (request == null
                || request.getLastWatchedSecond() == null
                || request.getVideoDurationSecond() == null
                || request.getLastWatchedSecond() < 0
                || request.getVideoDurationSecond() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (request.getWatchedPercentage() != null
                && (request.getWatchedPercentage().compareTo(ZERO) < 0
                || request.getWatchedPercentage().compareTo(ONE_HUNDRED) > 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void applyProgressRules(LessonProgress progress, int requestedSecond, int videoDurationSecond) {
        int previousLastSecond = progress.getLastWatchedSecond() == null ? 0 : progress.getLastWatchedSecond();
        int mergedLastSecond = Math.max(previousLastSecond, requestedSecond);
        progress.setLastWatchedSecond(mergedLastSecond);

        BigDecimal calculated = calculateWatchedPercentage(mergedLastSecond, videoDurationSecond);
        BigDecimal previousPercentage = progress.getWatchedPercentage() == null ? ZERO : progress.getWatchedPercentage();
        BigDecimal mergedPercentage = calculated.max(previousPercentage).min(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
        progress.setWatchedPercentage(mergedPercentage);

        boolean alreadyCompleted = progress.isCompleted();
        boolean reachedThreshold = mergedPercentage.compareTo(effectiveCompletionThreshold()) >= 0;
        if (!alreadyCompleted && reachedThreshold) {
            progress.setCompleted(true);
            progress.setCompletedAt(Instant.now());
        }
    }

    private BigDecimal calculateWatchedPercentage(int watchedSecond, int durationSecond) {
        if (durationSecond <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return BigDecimal.valueOf(watchedSecond)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(durationSecond), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveCompletionThreshold() {
        if (completionThresholdPercentage == null) {
            return DEFAULT_COMPLETION_THRESHOLD;
        }
        if (completionThresholdPercentage.compareTo(ZERO) <= 0
                || completionThresholdPercentage.compareTo(ONE_HUNDRED) > 0) {
            return DEFAULT_COMPLETION_THRESHOLD;
        }
        return completionThresholdPercentage;
    }

    private Course resolveCourseForProgress(AuthContext authContext, String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId.trim())
                .orElseThrow(CourseNotFoundException::new);
        ensureProgressAccess(authContext, course);
        return course;
    }

    private void ensureProgressAccess(AuthContext authContext, Course course) {
        String userId = requireUserId(authContext);
        UserRole role = authContext.role();

        if (role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.INSTRUCTOR) {
            boolean ownsCourse = courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull(course.getId(), userId);
            if (ownsCourse) {
                return;
            }
        }

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new CourseNotFoundException();
        }

        if (!isPositiveLong(userId)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        boolean enrolled = enrollmentAccessClient.hasActiveEnrollment(userId, course.getId());
        if (!enrolled) {
            throw new AccessDeniedException("Enrollment required");
        }
    }

    private Lesson resolveLesson(Course course, String lessonId) {
        if (lessonId == null || lessonId.isBlank() || course.getLessons() == null || course.getLessons().isEmpty()) {
            throw new LessonNotFoundException();
        }

        return course.getLessons().stream()
                .filter(item -> lessonId.equals(item.getId()))
                .findFirst()
                .orElseThrow(LessonNotFoundException::new);
    }

    private Set<String> lessonIdsOf(Course course) {
        if (course.getLessons() == null || course.getLessons().isEmpty()) {
            return Set.of();
        }
        Set<String> lessonIds = new HashSet<>();
        for (Lesson lesson : course.getLessons()) {
            if (lesson != null && lesson.getId() != null && !lesson.getId().isBlank()) {
                lessonIds.add(lesson.getId());
            }
        }
        return lessonIds;
    }

    private String requireUserId(AuthContext authContext) {
        if (authContext == null
                || authContext.userId() == null
                || authContext.userId().isBlank()
                || authContext.role() == null
                || authContext.role() == UserRole.UNKNOWN) {
            throw new AccessDeniedException("Authenticated user required");
        }
        return authContext.userId().trim();
    }

    private LessonProgressResponse toResponse(LessonProgress progress) {
        return LessonProgressResponse.builder()
                .courseId(progress.getCourseId())
                .lessonId(progress.getLessonId())
                .lastWatchedSecond(progress.getLastWatchedSecond())
                .watchedPercentage((progress.getWatchedPercentage() == null ? ZERO : progress.getWatchedPercentage())
                        .setScale(2, RoundingMode.HALF_UP))
                .completed(progress.isCompleted())
                .completedAt(progress.getCompletedAt())
                .updatedAt(progress.getUpdatedAt())
                .build();
    }

    private boolean isPositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
