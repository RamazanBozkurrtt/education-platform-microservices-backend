package com.edubase.course.service.concretes.finalexam;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.repository.LessonProgressRepository;
import com.edubase.course.service.abstracts.finalexam.CourseCompletionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LessonProgressCourseCompletionPolicy implements CourseCompletionPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_COMPLETION_THRESHOLD = BigDecimal.valueOf(90);

    private final LessonProgressRepository lessonProgressRepository;

    @Value("${course.progress.completion-threshold-percentage:90}")
    private BigDecimal completionThresholdPercentage;

    @Override
    public boolean isCompleted(String studentId, Course course) {
        if (studentId == null || studentId.isBlank() || course == null || course.getId() == null || course.getId().isBlank()) {
            return false;
        }

        Set<String> lessonIds = extractRequiredVideoLessonIds(course.getLessons());
        if (lessonIds.isEmpty()) {
            return true;
        }

        Map<String, LessonProgress> progressByLessonId = lessonProgressRepository
                .findByUserIdAndCourseId(studentId.trim(), course.getId().trim()).stream()
                .filter(progress -> progress.getLessonId() != null && !progress.getLessonId().isBlank())
                .collect(Collectors.toMap(
                        LessonProgress::getLessonId,
                        Function.identity(),
                        this::selectMostCompleteProgress
                ));

        BigDecimal threshold = effectiveCompletionThreshold();
        long completedLessonCount = lessonIds.stream()
                .map(progressByLessonId::get)
                .filter(progress -> isLessonCompleted(progress, threshold))
                .count();
        return completedLessonCount >= lessonIds.size();
    }

    private Set<String> extractRequiredVideoLessonIds(List<Lesson> lessons) {
        Set<String> lessonIds = new HashSet<>();
        if (lessons == null || lessons.isEmpty()) {
            return lessonIds;
        }
        for (Lesson lesson : lessons) {
            if (lesson == null) {
                continue;
            }
            if (lesson.getVideoUrl() == null || lesson.getVideoUrl().isBlank()) {
                continue;
            }
            if (lesson.getId() != null && !lesson.getId().isBlank()) {
                lessonIds.add(lesson.getId());
            }
        }
        return lessonIds;
    }

    private LessonProgress selectMostCompleteProgress(LessonProgress left, LessonProgress right) {
        int leftScore = completionScore(left);
        int rightScore = completionScore(right);
        return rightScore > leftScore ? right : left;
    }

    private int completionScore(LessonProgress progress) {
        if (progress == null) {
            return 0;
        }
        if (progress.isCompleted()) {
            return 3;
        }
        BigDecimal watched = progress.getWatchedPercentage();
        if (watched != null && watched.compareTo(effectiveCompletionThreshold()) >= 0) {
            return 2;
        }
        return 1;
    }

    private boolean isLessonCompleted(LessonProgress progress, BigDecimal threshold) {
        if (progress == null) {
            return false;
        }
        if (progress.isCompleted()) {
            return true;
        }
        BigDecimal watched = progress.getWatchedPercentage();
        return watched != null && watched.compareTo(threshold) >= 0;
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
}
