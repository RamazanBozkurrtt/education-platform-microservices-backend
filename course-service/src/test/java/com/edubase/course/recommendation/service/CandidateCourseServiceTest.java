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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateCourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CourseLevelRepository courseLevelRepository;

    private CandidateCourseService candidateCourseService;

    @BeforeEach
    void setUp() {
        RecommendationServiceProperties properties = new RecommendationServiceProperties();
        properties.setCandidateLimit(10);
        candidateCourseService = new CandidateCourseService(courseRepository, categoryRepository, courseLevelRepository, properties);
    }

    @Test
    void buildCandidates_usesSummedLessonDurations_andDefaultsToZeroWhenMissing() {
        Course durationCourse = Course.builder()
                .id("course-1")
                .title("Course 1")
                .description("Desc")
                .status(CourseStatus.PUBLISHED)
                .lessons(List.of(
                        Lesson.builder().id("l1").duration(120).build(),
                        Lesson.builder().id("l2").duration(180).build(),
                        Lesson.builder().id("l3").duration(null).build()))
                .build();
        Course missingDurationCourse = Course.builder()
                .id("course-2")
                .title("Course 2")
                .description("Desc")
                .status(CourseStatus.PUBLISHED)
                .lessons(List.of(
                        Lesson.builder().id("l4").duration(null).build(),
                        Lesson.builder().id("l5").duration(null).build()))
                .build();

        when(courseRepository.findAllByStatusAndDeletedAtIsNull(eq(CourseStatus.PUBLISHED), any()))
                .thenReturn(new PageImpl<>(List.of(durationCourse, missingDurationCourse)));

        UserRecommendationProfile profile = UserRecommendationProfile.builder()
                .coldStart(true)
                .completedCourseIds(List.of())
                .build();

        List<CandidateCourseData> candidates = candidateCourseService.buildCandidates(profile, null);

        assertEquals(2, candidates.size());
        assertEquals(300L, findByCourseId(candidates, "course-1").getDurationSeconds());
        assertEquals(0L, findByCourseId(candidates, "course-2").getDurationSeconds());
    }

    private CandidateCourseData findByCourseId(List<CandidateCourseData> candidates, String courseId) {
        return candidates.stream()
                .filter(candidate -> courseId.equals(candidate.getCourseId()))
                .findFirst()
                .orElseThrow();
    }
}
