package com.edubase.course.service;

import com.edubase.course.dto.request.finalexam.SubmitExamRequest;
import com.edubase.course.dto.response.finalexam.StartExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamOverviewResponse;
import com.edubase.course.dto.response.finalexam.SubmitExamResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.entity.finalexam.AttemptStatus;
import com.edubase.course.entity.finalexam.ExamAttempt;
import com.edubase.course.entity.finalexam.ExamAttemptAnswer;
import com.edubase.course.entity.finalexam.ExamOption;
import com.edubase.course.entity.finalexam.ExamQuestion;
import com.edubase.course.entity.finalexam.FinalExam;
import com.edubase.course.entity.finalexam.ResultStatus;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.repository.LessonProgressRepository;
import com.edubase.course.repository.finalexam.CertificateEligibilityRepository;
import com.edubase.course.repository.finalexam.ExamAttemptAnswerRepository;
import com.edubase.course.repository.finalexam.ExamAttemptRepository;
import com.edubase.course.repository.finalexam.ExamQuestionRepository;
import com.edubase.course.repository.finalexam.FinalExamRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.finalexam.CourseCompletionPolicy;
import com.edubase.course.service.concretes.EnrollmentAccessClient;
import com.edubase.course.service.concretes.finalexam.FinalExamStudentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalExamStudentServiceImplTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private FinalExamRepository finalExamRepository;
    @Mock
    private LessonProgressRepository lessonProgressRepository;
    @Mock
    private ExamQuestionRepository examQuestionRepository;
    @Mock
    private ExamAttemptRepository examAttemptRepository;
    @Mock
    private ExamAttemptAnswerRepository examAttemptAnswerRepository;
    @Mock
    private CertificateEligibilityRepository certificateEligibilityRepository;
    @Mock
    private EnrollmentAccessClient enrollmentAccessClient;
    @Mock
    private CourseCompletionPolicy courseCompletionPolicy;

    @InjectMocks
    private FinalExamStudentServiceImpl service;

    @Test
    void startAttempt_shouldReturnExistingActiveAttempt() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        ExamQuestion question = question(exam, 1L, 1L, true);
        ExamAttempt activeAttempt = ExamAttempt.builder()
                .id(99L)
                .finalExamId(exam.getId())
                .courseId(course.getId())
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now())
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(examQuestionRepository.findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(examAttemptRepository.findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(
                exam.getId(), "42", AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(activeAttempt));
        when(examAttemptRepository.countByFinalExamIdAndStudentIdAndAttemptStatusIn(
                exam.getId(), "42", java.util.Set.of(
                        AttemptStatus.IN_PROGRESS,
                        AttemptStatus.SUBMITTED,
                        AttemptStatus.EXPIRED,
                        AttemptStatus.TERMINATED
                )))
                .thenReturn(1L);

        StartExamAttemptResponse response = service.startAttempt(authContext, "course-1");

        assertFalse(response.isCreatedNewAttempt());
        assertEquals(99L, response.getAttempt().getAttemptId());
        assertEquals(2, response.getRemainingAttempts());
    }

    @Test
    void startAttempt_shouldConsumeAttemptOnCreate() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        ExamQuestion question = question(exam, 1L, 1L, true);

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(examQuestionRepository.findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(examAttemptRepository.findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(
                exam.getId(), "42", AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(examAttemptRepository.countByFinalExamIdAndStudentIdAndAttemptStatusIn(
                exam.getId(), "42", java.util.Set.of(
                        AttemptStatus.IN_PROGRESS,
                        AttemptStatus.SUBMITTED,
                        AttemptStatus.EXPIRED,
                        AttemptStatus.TERMINATED
                )))
                .thenReturn(0L);
        when(examAttemptRepository.countByFinalExamIdAndStudentId(exam.getId(), "42")).thenReturn(0L);
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> {
            ExamAttempt saved = invocation.getArgument(0);
            saved.setId(1000L);
            return saved;
        });

        StartExamAttemptResponse response = service.startAttempt(authContext, "course-1");

        assertTrue(response.isCreatedNewAttempt());
        assertEquals(2, response.getRemainingAttempts());
        assertEquals(1000L, response.getAttempt().getAttemptId());
        assertEquals(AttemptStatus.IN_PROGRESS, response.getAttempt().getAttemptStatus());
    }

    @Test
    void startAttempt_shouldCreateNewAttemptWhenExistingInProgressAttemptHasDifferentCourseId() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        ExamQuestion question = question(exam, 1L, 1L, true);
        ExamAttempt inconsistentActiveAttempt = ExamAttempt.builder()
                .id(88L)
                .finalExamId(exam.getId())
                .courseId("legacy-course")
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now())
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(examQuestionRepository.findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(examAttemptRepository.findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(
                exam.getId(), "42", AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.of(inconsistentActiveAttempt));
        when(examAttemptRepository.countByFinalExamIdAndStudentIdAndAttemptStatusIn(
                exam.getId(), "42", java.util.Set.of(
                        AttemptStatus.IN_PROGRESS,
                        AttemptStatus.SUBMITTED,
                        AttemptStatus.EXPIRED,
                        AttemptStatus.TERMINATED
                )))
                .thenReturn(1L);
        when(examAttemptRepository.countByFinalExamIdAndStudentId(exam.getId(), "42")).thenReturn(1L);
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> {
            ExamAttempt saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1000L);
            }
            return saved;
        });

        StartExamAttemptResponse response = service.startAttempt(authContext, "course-1");

        assertTrue(response.isCreatedNewAttempt());
        assertEquals(1000L, response.getAttempt().getAttemptId());
        assertEquals(AttemptStatus.IN_PROGRESS, response.getAttempt().getAttemptStatus());
        assertEquals(AttemptStatus.TERMINATED, inconsistentActiveAttempt.getAttemptStatus());
        verify(examAttemptRepository, atLeastOnce()).save(inconsistentActiveAttempt);
    }

    @Test
    void startAttempt_shouldCreateAttemptWithExpectedIdsAndGetAttemptShouldLoadSameAttempt() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        ExamQuestion question = question(exam, 1L, 1L, true);
        AtomicReference<ExamAttempt> savedAttemptRef = new AtomicReference<>();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(examQuestionRepository.findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(examAttemptRepository.findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(
                exam.getId(), "42", AttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(examAttemptRepository.countByFinalExamIdAndStudentIdAndAttemptStatusIn(
                exam.getId(), "42", java.util.Set.of(
                        AttemptStatus.IN_PROGRESS,
                        AttemptStatus.SUBMITTED,
                        AttemptStatus.EXPIRED,
                        AttemptStatus.TERMINATED
                )))
                .thenReturn(0L);
        when(examAttemptRepository.countByFinalExamIdAndStudentId(exam.getId(), "42")).thenReturn(0L);
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> {
            ExamAttempt saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1001L);
            }
            savedAttemptRef.set(saved);
            return saved;
        });
        when(finalExamRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examAttemptAnswerRepository.findByAttemptId(1001L)).thenReturn(List.of());

        StartExamAttemptResponse startResponse = service.startAttempt(authContext, "course-1");

        assertTrue(startResponse.isCreatedNewAttempt());
        assertNotNull(savedAttemptRef.get());
        assertEquals("course-1", savedAttemptRef.get().getCourseId());
        assertEquals(exam.getId(), savedAttemptRef.get().getFinalExamId());
        assertEquals("42", savedAttemptRef.get().getStudentId());

        when(examAttemptRepository.findByIdAndCourseId(1001L, "course-1")).thenReturn(Optional.of(savedAttemptRef.get()));

        StudentExamAttemptResponse attemptResponse = service.getAttempt(authContext, "course-1", 1001L);

        assertEquals(1001L, startResponse.getAttempt().getAttemptId());
        assertEquals(1001L, attemptResponse.getAttemptId());
        assertEquals("course-1", attemptResponse.getCourseId());
        assertEquals(exam.getId(), attemptResponse.getFinalExamId());
        assertEquals(AttemptStatus.IN_PROGRESS, attemptResponse.getAttemptStatus());
        assertFalse(attemptResponse.getQuestions().isEmpty());
    }

    @Test
    void submitAttempt_shouldCalculateScoreAndCreateCertificateEligibility() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        FinalExam exam = sampleExam();
        ExamAttempt attempt = ExamAttempt.builder()
                .id(123L)
                .finalExamId(exam.getId())
                .courseId("course-1")
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now())
                .build();
        ExamQuestion question = question(exam, 1L, 11L, true);
        ExamAttemptAnswer answer = ExamAttemptAnswer.builder()
                .attemptId(attempt.getId())
                .questionId(question.getId())
                .selectedOptionId(11L)
                .answeredAt(Instant.now())
                .build();

        when(examAttemptRepository.findByIdAndCourseId(123L, "course-1")).thenReturn(Optional.of(attempt));
        when(finalExamRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(exam.getId()))
                .thenReturn(List.of(question));
        when(examAttemptAnswerRepository.findByAttemptIdAndQuestionIdIn(attempt.getId(), java.util.Set.of(question.getId())))
                .thenReturn(List.of(answer));
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(certificateEligibilityRepository.findByCourseIdAndStudentId("course-1", "42")).thenReturn(Optional.empty());
        when(certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue("course-1", "42")).thenReturn(true);
        when(examAttemptRepository.countByFinalExamIdAndStudentIdAndAttemptStatusIn(
                exam.getId(), "42", java.util.Set.of(
                        AttemptStatus.IN_PROGRESS,
                        AttemptStatus.SUBMITTED,
                        AttemptStatus.EXPIRED,
                        AttemptStatus.TERMINATED
                )))
                .thenReturn(1L);

        SubmitExamResponse response = service.submitAttempt(authContext, "course-1", 123L, new SubmitExamRequest());

        assertTrue(response.isPassed());
        assertEquals(new BigDecimal("100.00"), response.getScore());
        assertEquals(ResultStatus.PASSED, response.getResultStatus());
        verify(certificateEligibilityRepository).save(any());
    }

    @Test
    void getOverview_shouldCountInProgressAttemptAsConsumedAndBlockStart() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        ExamAttempt activeAttempt = ExamAttempt.builder()
                .id(200L)
                .finalExamId(exam.getId())
                .courseId(course.getId())
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now())
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue("course-1", "42")).thenReturn(false);
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(progress("42", "course-1", "lesson-1", true, new BigDecimal("100.00"))));
        when(examAttemptRepository.findByFinalExamIdAndStudentIdOrderByAttemptNumberDesc(exam.getId(), "42"))
                .thenReturn(List.of(activeAttempt));

        StudentExamOverviewResponse response = service.getOverview(authContext, "course-1");

        assertTrue(response.isHasActiveAttempt());
        assertEquals(200L, response.getActiveAttemptId());
        assertEquals(2, response.getRemainingAttempts());
        assertFalse(response.isCanStartExam());
        assertEquals("Devam eden bir sınav oturumunuz var. Mevcut oturuma devam edebilirsiniz.", response.getCanStartReason());
    }

    @Test
    void getOverview_shouldExpireStaleInProgressAttempt() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        exam.setDurationMinutes(1);
        ExamAttempt staleAttempt = ExamAttempt.builder()
                .id(201L)
                .finalExamId(exam.getId())
                .courseId(course.getId())
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now().minusSeconds(120))
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue("course-1", "42")).thenReturn(false);
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(progress("42", "course-1", "lesson-1", true, new BigDecimal("100.00"))));
        when(examAttemptRepository.findByFinalExamIdAndStudentIdOrderByAttemptNumberDesc(exam.getId(), "42"))
                .thenReturn(List.of(staleAttempt));
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentExamOverviewResponse response = service.getOverview(authContext, "course-1");

        assertFalse(response.isHasActiveAttempt());
        assertNull(response.getActiveAttemptId());
        assertEquals(2, response.getRemainingAttempts());
        assertTrue(response.isCanStartExam());
        assertEquals(AttemptStatus.EXPIRED, staleAttempt.getAttemptStatus());
    }

    @Test
    void getOverview_shouldIgnoreInProgressAttemptWithDifferentCourseId() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();
        ExamAttempt inconsistentActiveAttempt = ExamAttempt.builder()
                .id(202L)
                .finalExamId(exam.getId())
                .courseId("legacy-course")
                .studentId("42")
                .attemptNumber(2)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now())
                .build();
        ExamAttempt terminatedAttempt = ExamAttempt.builder()
                .id(201L)
                .finalExamId(exam.getId())
                .courseId("course-1")
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.TERMINATED)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now().minusSeconds(60))
                .terminatedAt(Instant.now().minusSeconds(30))
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue("course-1", "42")).thenReturn(false);
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(true);
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(progress("42", "course-1", "lesson-1", true, new BigDecimal("100.00"))));
        when(examAttemptRepository.findByFinalExamIdAndStudentIdOrderByAttemptNumberDesc(exam.getId(), "42"))
                .thenReturn(List.of(inconsistentActiveAttempt, terminatedAttempt));
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentExamOverviewResponse response = service.getOverview(authContext, "course-1");

        assertFalse(response.isHasActiveAttempt());
        assertNull(response.getActiveAttemptId());
        assertEquals(1, response.getRemainingAttempts());
        assertTrue(response.isCanStartExam());
        assertEquals(AttemptStatus.TERMINATED, inconsistentActiveAttempt.getAttemptStatus());
        verify(examAttemptRepository).save(inconsistentActiveAttempt);
    }

    @Test
    void getOverview_shouldExplainWhenCourseNotCompleted() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        Course course = publishedCourse();
        FinalExam exam = sampleExam();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(enrollmentAccessClient.hasActiveEnrollment("42", "course-1")).thenReturn(true);
        when(finalExamRepository.findByCourseIdAndActiveTrue("course-1")).thenReturn(Optional.of(exam));
        when(certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue("course-1", "42")).thenReturn(false);
        when(courseCompletionPolicy.isCompleted("42", course)).thenReturn(false);
        when(lessonProgressRepository.findByUserIdAndCourseId("42", "course-1"))
                .thenReturn(List.of(progress("42", "course-1", "lesson-1", false, new BigDecimal("80.00"))));
        when(examAttemptRepository.findByFinalExamIdAndStudentIdOrderByAttemptNumberDesc(exam.getId(), "42"))
                .thenReturn(List.of());

        StudentExamOverviewResponse response = service.getOverview(authContext, "course-1");

        assertFalse(response.isCanStartExam());
        assertFalse(response.isCourseCompleted());
        assertEquals("Final sınavına başlayabilmek için kurs videolarını tamamlamalısınız.", response.getCanStartReason());
    }

    @Test
    void getAttempt_shouldHideQuestionsWhenAttemptExpired() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        FinalExam exam = sampleExam();
        exam.setDurationMinutes(1);
        ExamAttempt staleAttempt = ExamAttempt.builder()
                .id(321L)
                .finalExamId(exam.getId())
                .courseId("course-1")
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now().minusSeconds(120))
                .build();

        when(examAttemptRepository.findByIdAndCourseId(321L, "course-1")).thenReturn(Optional.of(staleAttempt));
        when(finalExamRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudentExamAttemptResponse response = service.getAttempt(authContext, "course-1", 321L);

        assertEquals(AttemptStatus.EXPIRED, response.getAttemptStatus());
        assertEquals("Sinav oturumunuzun suresi doldu.", response.getStatusMessage());
        assertTrue(response.getQuestions().isEmpty());
        verify(examQuestionRepository, never()).findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(anyLong());
    }

    @Test
    void getAttempt_shouldHideQuestionsWhenAttemptTerminated() {
        AuthContext authContext = new AuthContext("42", UserRole.USER);
        FinalExam exam = sampleExam();
        ExamAttempt terminatedAttempt = ExamAttempt.builder()
                .id(322L)
                .finalExamId(exam.getId())
                .courseId("course-1")
                .studentId("42")
                .attemptNumber(1)
                .attemptStatus(AttemptStatus.TERMINATED)
                .resultStatus(ResultStatus.NONE)
                .startedAt(Instant.now().minusSeconds(120))
                .terminatedAt(Instant.now().minusSeconds(30))
                .build();

        when(examAttemptRepository.findByIdAndCourseId(322L, "course-1")).thenReturn(Optional.of(terminatedAttempt));
        when(finalExamRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        StudentExamAttemptResponse response = service.getAttempt(authContext, "course-1", 322L);

        assertEquals(AttemptStatus.TERMINATED, response.getAttemptStatus());
        assertEquals("Bu sinav oturumu sonlandirildi.", response.getStatusMessage());
        assertTrue(response.getQuestions().isEmpty());
        verify(examQuestionRepository, never()).findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(anyLong());
    }

    private Course publishedCourse() {
        return Course.builder()
                .id("course-1")
                .status(CourseStatus.PUBLISHED)
                .lessons(List.of(Lesson.builder().id("lesson-1").videoUrl("/videos/lesson-1").build()))
                .build();
    }

    private FinalExam sampleExam() {
        return FinalExam.builder()
                .id(10L)
                .courseId("course-1")
                .title("Final")
                .questionCount(1)
                .durationMinutes(60)
                .maxAttempts(3)
                .availabilityDays(3)
                .passingScore(BigDecimal.valueOf(70))
                .active(true)
                .build();
    }

    private ExamQuestion question(FinalExam exam, Long questionId, Long correctOptionId, boolean active) {
        ExamOption correct = ExamOption.builder()
                .id(correctOptionId)
                .correct(true)
                .optionText("A")
                .orderIndex(0)
                .build();
        ExamOption wrong = ExamOption.builder()
                .id(correctOptionId + 1)
                .correct(false)
                .optionText("B")
                .orderIndex(1)
                .build();
        return ExamQuestion.builder()
                .id(questionId)
                .finalExam(exam)
                .questionText("Q")
                .orderIndex(0)
                .active(active)
                .options(List.of(correct, wrong))
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
