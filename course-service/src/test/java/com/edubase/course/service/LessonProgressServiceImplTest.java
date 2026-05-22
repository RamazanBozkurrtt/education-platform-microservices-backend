package com.edubase.course.service;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.dto.request.LessonProgressUpdateRequest;
import com.edubase.course.dto.response.LessonProgressResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.repository.LessonProgressRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.concretes.EnrollmentAccessClient;
import com.edubase.course.service.concretes.LessonProgressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonProgressServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private EnrollmentAccessClient enrollmentAccessClient;

    @InjectMocks
    private LessonProgressServiceImpl lessonProgressService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(lessonProgressService, "completionThresholdPercentage", BigDecimal.valueOf(90));
    }

    @Test
    void shouldCreateProgressOnFirstUpdate() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(120)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId("42", "course-1", "lesson-1"))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(invocation -> {
            LessonProgress progress = invocation.getArgument(0);
            progress.setId(1L);
            progress.setUpdatedAt(Instant.now());
            return progress;
        });

        LessonProgressResponse response = lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request);

        assertEquals(120, response.getLastWatchedSecond());
        assertEquals(new BigDecimal("20.00"), response.getWatchedPercentage());
        assertFalse(response.isCompleted());
    }

    @Test
    void shouldNotDecreaseLastWatchedSecond() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        LessonProgress existing = LessonProgress.builder()
                .id(10L)
                .userId("42")
                .courseId("course-1")
                .lessonId("lesson-1")
                .lastWatchedSecond(240)
                .watchedPercentage(new BigDecimal("40.00"))
                .completed(false)
                .build();
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(100)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId("42", "course-1", "lesson-1"))
                .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(invocation -> {
            LessonProgress progress = invocation.getArgument(0);
            progress.setUpdatedAt(Instant.now());
            return progress;
        });

        LessonProgressResponse response = lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request);

        assertEquals(240, response.getLastWatchedSecond());
        assertEquals(new BigDecimal("40.00"), response.getWatchedPercentage());
    }

    @Test
    void shouldMarkCompletedWhenThresholdReached() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(540)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId("42", "course-1", "lesson-1"))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(invocation -> {
            LessonProgress progress = invocation.getArgument(0);
            progress.setUpdatedAt(Instant.now());
            return progress;
        });

        LessonProgressResponse response = lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request);

        assertTrue(response.isCompleted());
        assertNotNull(response.getCompletedAt());
        assertEquals(new BigDecimal("90.00"), response.getWatchedPercentage());
    }

    @Test
    void shouldNotResetCompletedAtAfterCompletion() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Instant completedAt = Instant.parse("2026-05-20T10:15:30Z");
        LessonProgress existing = LessonProgress.builder()
                .id(99L)
                .userId("42")
                .courseId("course-1")
                .lessonId("lesson-1")
                .lastWatchedSecond(590)
                .watchedPercentage(new BigDecimal("98.33"))
                .completed(true)
                .completedAt(completedAt)
                .build();
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(400)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId("42", "course-1", "lesson-1"))
                .thenReturn(Optional.of(existing));
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(invocation -> {
            LessonProgress progress = invocation.getArgument(0);
            progress.setUpdatedAt(Instant.now());
            return progress;
        });

        LessonProgressResponse response = lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request);

        assertTrue(response.isCompleted());
        assertEquals(completedAt, response.getCompletedAt());
    }

    @Test
    void shouldClampWatchedSecondToDuration() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(700)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId("42", "course-1", "lesson-1"))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(LessonProgress.class))).thenAnswer(invocation -> {
            LessonProgress progress = invocation.getArgument(0);
            progress.setUpdatedAt(Instant.now());
            return progress;
        });

        LessonProgressResponse response = lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request);

        assertEquals(600, response.getLastWatchedSecond());
        assertEquals(new BigDecimal("100.00"), response.getWatchedPercentage());
        assertTrue(response.isCompleted());
    }

    @Test
    void shouldRejectWhenUserIsNotEnrolled() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(60)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request));
    }

    @Test
    void shouldReturnUnauthorizedWhenUserIdIsNotNumericForStudentFlow() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("abc-user", UserRole.USER);
        LessonProgressUpdateRequest request = LessonProgressUpdateRequest.builder()
                .lastWatchedSecond(60)
                .videoDurationSecond(600)
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> lessonProgressService.updateLessonProgress(authContext, "course-1", "lesson-1", request));
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void shouldReturnDefaultProgressWhenNoRecord() {
        Course course = publishedCourse();
        AuthContext authContext = new AuthContext("42", UserRole.USER);

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId("42", "course-1", "lesson-1"))
                .thenReturn(Optional.empty());

        LessonProgressResponse response = lessonProgressService.getLessonProgress(authContext, "course-1", "lesson-1");

        assertEquals(0, response.getLastWatchedSecond());
        assertEquals(new BigDecimal("0.00"), response.getWatchedPercentage());
        assertFalse(response.isCompleted());
    }

    private Course publishedCourse() {
        return Course.builder()
                .id("course-1")
                .status(CourseStatus.PUBLISHED)
                .lessons(List.of(Lesson.builder().id("lesson-1").build()))
                .build();
    }
}
