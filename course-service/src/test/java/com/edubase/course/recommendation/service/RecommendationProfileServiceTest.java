package com.edubase.course.recommendation.service;

import com.edubase.course.entity.Category;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseLevel;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.recommendation.config.RecommendationServiceProperties;
import com.edubase.course.recommendation.model.UserRecommendationProfile;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.repository.CourseLevelRepository;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.repository.LessonProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationProfileServiceTest {

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CourseLevelRepository courseLevelRepository;

    @Mock
    private RecommendationServiceProperties properties;

    @InjectMocks
    private RecommendationProfileService recommendationProfileService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recommendationProfileService, "completionThresholdPercentage", BigDecimal.valueOf(90));
    }

    @Test
    void buildProfile_shouldReturnColdStartWhenNoProgress() {
        when(properties.getDefaultPreferredDurationSeconds()).thenReturn(5400);
        when(lessonProgressRepository.findByUserIdOrderByUpdatedAtDesc("42")).thenReturn(List.of());

        UserRecommendationProfile profile = recommendationProfileService.buildProfile("42");

        assertTrue(profile.isColdStart());
        assertEquals(0.0d, profile.getAverageCompletionRate());
        assertEquals(5400L, profile.getPreferredDurationSeconds());
        assertEquals(List.of(), profile.getCompletedCourseIds());
    }

    @Test
    void buildProfile_shouldComputeProgressMetrics() {
        when(properties.getRecentCourseLimit()).thenReturn(5);
        when(properties.getDropoutLowProgressThresholdPercent()).thenReturn(25.0d);

        LessonProgress completedProgress = LessonProgress.builder()
                .courseId("course-1")
                .lessonId("lesson-1")
                .watchedPercentage(new BigDecimal("95.00"))
                .updatedAt(Instant.parse("2026-05-29T09:00:00Z"))
                .build();
        LessonProgress dropoutProgress = LessonProgress.builder()
                .courseId("course-2")
                .lessonId("lesson-2")
                .watchedPercentage(new BigDecimal("10.00"))
                .updatedAt(Instant.parse("2026-05-29T08:00:00Z"))
                .build();

        Course completedCourse = Course.builder()
                .id("course-1")
                .status(CourseStatus.PUBLISHED)
                .categoryIds(List.of("cat-1"))
                .levelId("lvl-1")
                .lessons(List.of(
                        Lesson.builder().id("l1").duration(1800).build(),
                        Lesson.builder().id("l2").duration(1800).build()))
                .build();
        Course dropoutCourse = Course.builder()
                .id("course-2")
                .status(CourseStatus.PUBLISHED)
                .categoryIds(List.of("cat-1"))
                .levelId("lvl-1")
                .lessons(List.of(Lesson.builder().id("l3").duration(1200).build()))
                .build();

        when(lessonProgressRepository.findByUserIdOrderByUpdatedAtDesc("42"))
                .thenReturn(List.of(completedProgress, dropoutProgress));
        when(courseRepository.findAllByIdInAndStatusAndDeletedAtIsNull(anyCollection(), org.mockito.ArgumentMatchers.eq(CourseStatus.PUBLISHED)))
                .thenReturn(List.of(completedCourse, dropoutCourse));
        when(categoryRepository.findAllById(anyCollection()))
                .thenReturn(List.of(new Category("cat-1", "Backend", null, null)));
        when(courseLevelRepository.findAllById(anyCollection()))
                .thenReturn(List.of(new CourseLevel("lvl-1", "Beginner", 1, null, null)));

        UserRecommendationProfile profile = recommendationProfileService.buildProfile("42");

        assertFalse(profile.isColdStart());
        assertEquals(List.of("course-1"), profile.getCompletedCourseIds());
        assertEquals(List.of("course-2"), profile.getInProgressCourseIds());
        assertEquals(52.5d, profile.getAverageCompletionRate());
        assertEquals(50.0d, profile.getDropoutRate());
        assertEquals(3600L, profile.getPreferredDurationSeconds());
        assertEquals(List.of("Backend"), profile.getFavoriteCategories());
        assertEquals(List.of("Beginner"), profile.getPreferredLevels());
    }
}
