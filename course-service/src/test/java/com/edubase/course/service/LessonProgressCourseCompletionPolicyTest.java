package com.edubase.course.service;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.repository.LessonProgressRepository;
import com.edubase.course.service.concretes.finalexam.LessonProgressCourseCompletionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonProgressCourseCompletionPolicyTest {

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    private LessonProgressCourseCompletionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LessonProgressCourseCompletionPolicy(lessonProgressRepository);
        ReflectionTestUtils.setField(policy, "completionThresholdPercentage", BigDecimal.valueOf(90));
    }

    @Test
    void isCompleted_shouldReturnTrue_whenNoLessonExists() {
        Course course = Course.builder()
                .id("course-1")
                .lessons(List.of())
                .build();

        assertTrue(policy.isCompleted("42", course));
    }

    @Test
    void isCompleted_shouldReturnTrue_whenAllCurrentLessonsCompleted() {
        Course course = courseWithLessons("l1", "l2");
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(
                        progress("42", "course-1", "l1", true, new BigDecimal("30.00")),
                        progress("42", "course-1", "l2", true, new BigDecimal("50.00"))
                ));

        assertTrue(policy.isCompleted("42", course));
    }

    @Test
    void isCompleted_shouldReturnTrue_whenLegacyRowsHaveThresholdButCompletedFalse() {
        Course course = courseWithLessons("l1", "l2");
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(
                        progress("42", "course-1", "l1", false, new BigDecimal("90.00")),
                        progress("42", "course-1", "l2", false, new BigDecimal("99.50"))
                ));

        assertTrue(policy.isCompleted("42", course));
    }

    @Test
    void isCompleted_shouldReturnFalse_whenAtLeastOneLessonIncomplete() {
        Course course = courseWithLessons("l1", "l2", "l3");
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(
                        progress("42", "course-1", "l1", true, new BigDecimal("100.00")),
                        progress("42", "course-1", "l2", false, new BigDecimal("80.00"))
                ));

        assertFalse(policy.isCompleted("42", course));
    }

    @Test
    void isCompleted_shouldIgnoreLessonsWithoutVideo() {
        Course course = Course.builder()
                .id("course-1")
                .lessons(List.of(
                        Lesson.builder().id("l1").videoUrl("/videos/l1").build(),
                        Lesson.builder().id("l2").videoUrl("").build(),
                        Lesson.builder().id("l3").videoUrl(null).build()
                ))
                .build();
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(progress("42", "course-1", "l1", true, new BigDecimal("100.00"))));

        assertTrue(policy.isCompleted("42", course));
    }

    private Course courseWithLessons(String... lessonIds) {
        return Course.builder()
                .id("course-1")
                .lessons(List.of(lessonIds).stream()
                        .map(id -> Lesson.builder().id(id).videoUrl("/videos/" + id).build())
                        .toList())
                .build();
    }

    private LessonProgress progress(String userId, String courseId, String lessonId, boolean completed, BigDecimal watchedPercentage) {
        return LessonProgress.builder()
                .userId(userId)
                .courseId(courseId)
                .lessonId(lessonId)
                .completed(completed)
                .watchedPercentage(watchedPercentage)
                .lastWatchedSecond(100)
                .build();
    }
}
