package com.edubase.course.service.concretes.finalexam;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.dto.request.finalexam.SaveExamAnswerRequest;
import com.edubase.course.dto.request.finalexam.SubmitExamRequest;
import com.edubase.course.dto.response.finalexam.AttemptSummaryResponse;
import com.edubase.course.dto.response.finalexam.StartExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamAttemptResponse;
import com.edubase.course.dto.response.finalexam.StudentExamOverviewResponse;
import com.edubase.course.dto.response.finalexam.SubmitExamResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.entity.LessonProgress;
import com.edubase.course.entity.finalexam.AttemptStatus;
import com.edubase.course.entity.finalexam.CertificateEligibility;
import com.edubase.course.entity.finalexam.ExamAttempt;
import com.edubase.course.entity.finalexam.ExamAttemptAnswer;
import com.edubase.course.entity.finalexam.ExamOption;
import com.edubase.course.entity.finalexam.ExamQuestion;
import com.edubase.course.entity.finalexam.FinalExam;
import com.edubase.course.entity.finalexam.ResultStatus;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.FinalExamAttemptInvalidStateException;
import com.edubase.course.exception.FinalExamAttemptNotFoundException;
import com.edubase.course.exception.FinalExamAttemptsExhaustedException;
import com.edubase.course.exception.FinalExamCourseNotCompletedException;
import com.edubase.course.exception.FinalExamNotFoundException;
import com.edubase.course.exception.FinalExamNotReadyException;
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
import com.edubase.course.service.abstracts.finalexam.FinalExamStudentService;
import com.edubase.course.service.concretes.EnrollmentAccessClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinalExamStudentServiceImpl implements FinalExamStudentService {

    private static final Set<AttemptStatus> CONSUMING_STATUSES = EnumSet.allOf(AttemptStatus.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_COMPLETION_THRESHOLD = BigDecimal.valueOf(90);

    private final CourseRepository courseRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final FinalExamRepository finalExamRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamAttemptAnswerRepository examAttemptAnswerRepository;
    private final CertificateEligibilityRepository certificateEligibilityRepository;
    private final EnrollmentAccessClient enrollmentAccessClient;
    private final CourseCompletionPolicy courseCompletionPolicy;

    @Value("${course.progress.completion-threshold-percentage:90}")
    private BigDecimal completionThresholdPercentage;

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#authContext)")
    public StudentExamOverviewResponse getOverview(AuthContext authContext, String courseId) {
        String studentId = requireStudentId(authContext);
        Course course = resolvePublishedCourseForStudent(studentId, courseId);

        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(course.getId()).orElse(null);
        boolean certificateEligible = certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue(course.getId(), studentId);
        boolean courseCompleted = courseCompletionPolicy.isCompleted(studentId, course);
        ProgressSnapshot progressSnapshot = computeProgressSnapshot(studentId, course);

        if (finalExam == null) {
            return StudentExamOverviewResponse.builder()
                    .examExists(false)
                    .examActive(false)
                    .attempts(List.of())
                    .maxAttempts(0)
                    .remainingAttempts(0)
                    .totalVideos(progressSnapshot.totalVideos())
                    .completedVideos(progressSnapshot.completedVideos())
                    .completionPercentage(progressSnapshot.completionPercentage())
                    .hasActiveAttempt(false)
                    .activeAttemptId(null)
                    .activeAttemptExpiresAt(null)
                    .courseCompleted(courseCompleted)
                    .canStartExam(false)
                    .canStartReason("Bu kurs için aktif bir final sınavı bulunmuyor.")
                    .lastAttemptStatus(null)
                    .certificateEligible(certificateEligible)
                    .build();
        }

        Instant now = Instant.now();
        List<ExamAttempt> attempts = examAttemptRepository.findByFinalExamIdAndStudentIdOrderByAttemptNumberDesc(finalExam.getId(), studentId);
        List<ExamAttempt> adjustedAttempts = normalizeInProgressAttemptsIfNeeded(
                attempts,
                finalExam,
                course.getId(),
                studentId,
                now
        );
        ExamAttempt activeAttempt = adjustedAttempts.stream()
                .filter(attempt -> isContinuableInProgressAttempt(attempt, finalExam, course.getId()))
                .findFirst()
                .orElse(null);
        ExamAttempt lastAttempt = adjustedAttempts.stream()
                .max(Comparator.comparing(ExamAttempt::getAttemptNumber))
                .orElse(null);

        int consumedAttempts = countConsumedAttempts(adjustedAttempts);
        int remainingAttempts = Math.max(0, finalExam.getMaxAttempts() - consumedAttempts);
        boolean canStart = finalExam.isActive() && courseCompleted && remainingAttempts > 0 && activeAttempt == null;
        String canStartReason = resolveCanStartReason(finalExam, courseCompleted, activeAttempt, remainingAttempts);

        return StudentExamOverviewResponse.builder()
                .examExists(true)
                .examActive(finalExam.isActive())
                .finalExamId(finalExam.getId())
                .title(finalExam.getTitle())
                .description(finalExam.getDescription())
                .totalQuestionCount(finalExam.getQuestionCount())
                .durationMinutes(finalExam.getDurationMinutes())
                .passingScore(finalExam.getPassingScore())
                .maxAttempts(finalExam.getMaxAttempts())
                .remainingAttempts(remainingAttempts)
                .totalVideos(progressSnapshot.totalVideos())
                .completedVideos(progressSnapshot.completedVideos())
                .completionPercentage(progressSnapshot.completionPercentage())
                .attempts(adjustedAttempts.stream().map(this::toAttemptSummary).toList())
                .hasActiveAttempt(activeAttempt != null)
                .activeAttemptId(activeAttempt == null ? null : activeAttempt.getId())
                .activeAttemptExpiresAt(activeAttempt == null ? null : computeExpiryTime(activeAttempt, finalExam))
                .courseCompleted(courseCompleted)
                .canStartExam(canStart)
                .canStartReason(canStartReason)
                .lastAttemptStatus(lastAttempt == null ? null : lastAttempt.getAttemptStatus())
                .certificateEligible(certificateEligible)
                .build();
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#authContext)")
    public StartExamAttemptResponse startAttempt(AuthContext authContext, String courseId) {
        String studentId = requireStudentId(authContext);
        Course course = resolvePublishedCourseForStudent(studentId, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(course.getId()).orElseThrow(FinalExamNotFoundException::new);
        if (!finalExam.isActive()) {
            throw new FinalExamNotFoundException();
        }

        if (!courseCompletionPolicy.isCompleted(studentId, course)) {
            throw new FinalExamCourseNotCompletedException();
        }

        validateExamReadyForStudents(finalExam);

        Instant now = Instant.now();
        ExamAttempt activeAttempt = examAttemptRepository
                .findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(finalExam.getId(), studentId, AttemptStatus.IN_PROGRESS)
                .orElse(null);
        activeAttempt = normalizeCandidateActiveAttempt(activeAttempt, finalExam, course.getId(), studentId, now);

        int consumedAttempts = countConsumedAttempts(finalExam.getId(), studentId);
        int remainingAttempts = Math.max(0, finalExam.getMaxAttempts() - consumedAttempts);
        if (activeAttempt != null) {
            return StartExamAttemptResponse.builder()
                    .createdNewAttempt(false)
                    .remainingAttempts(remainingAttempts)
                    .attempt(toAttemptSummary(activeAttempt))
                    .build();
        }

        if (remainingAttempts <= 0) {
            throw new FinalExamAttemptsExhaustedException();
        }

        int nextAttemptNumber = (int) examAttemptRepository.countByFinalExamIdAndStudentId(finalExam.getId(), studentId) + 1;
        ExamAttempt created = ExamAttempt.builder()
                .finalExamId(finalExam.getId())
                .courseId(course.getId())
                .studentId(studentId)
                .attemptNumber(nextAttemptNumber)
                .attemptStatus(AttemptStatus.IN_PROGRESS)
                .resultStatus(ResultStatus.NONE)
                .startedAt(now)
                .passed(false)
                .createdBy(studentId)
                .updatedBy(studentId)
                .build();

        ExamAttempt saved;
        try {
            saved = examAttemptRepository.save(created);
        } catch (DataIntegrityViolationException ex) {
            ExamAttempt latestActive = examAttemptRepository
                    .findFirstByFinalExamIdAndStudentIdAndAttemptStatusOrderByStartedAtDesc(finalExam.getId(), studentId, AttemptStatus.IN_PROGRESS)
                    .orElse(null);
            latestActive = normalizeCandidateActiveAttempt(latestActive, finalExam, course.getId(), studentId, Instant.now());
            if (latestActive != null) {
                int updatedConsumedAttempts = countConsumedAttempts(finalExam.getId(), studentId);
                int updatedRemainingAttempts = Math.max(0, finalExam.getMaxAttempts() - updatedConsumedAttempts);
                return StartExamAttemptResponse.builder()
                        .createdNewAttempt(false)
                        .remainingAttempts(updatedRemainingAttempts)
                        .attempt(toAttemptSummary(latestActive))
                        .build();
            }
            saved = examAttemptRepository.save(created);
        }
        return StartExamAttemptResponse.builder()
                .createdNewAttempt(true)
                .remainingAttempts(Math.max(0, remainingAttempts - 1))
                .attempt(toAttemptSummary(saved))
                .build();
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#authContext)")
    public StudentExamAttemptResponse getAttempt(AuthContext authContext, String courseId, Long attemptId) {
        String studentId = requireStudentId(authContext);
        log.info("Final exam getAttempt requested userId={} courseId={} attemptId={}", studentId, courseId, attemptId);
        ExamAttempt attempt = resolveOwnedAttempt(studentId, courseId, attemptId);
        FinalExam finalExam = finalExamRepository.findById(attempt.getFinalExamId()).orElseThrow(FinalExamNotFoundException::new);
        expireIfNeeded(attempt, finalExam, Instant.now());
        boolean includeQuestions = attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS;
        return buildStudentAttemptResponse(attempt, finalExam, includeQuestions);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#authContext)")
    public StudentExamAttemptResponse saveAnswers(
            AuthContext authContext,
            String courseId,
            Long attemptId,
            SaveExamAnswerRequest request
    ) {
        String studentId = requireStudentId(authContext);
        ExamAttempt attempt = resolveOwnedAttempt(studentId, courseId, attemptId);
        FinalExam finalExam = finalExamRepository.findById(attempt.getFinalExamId()).orElseThrow(FinalExamNotFoundException::new);
        if (expireIfNeeded(attempt, finalExam, Instant.now())) {
            return buildStudentAttemptResponse(attempt, finalExam, false);
        }
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            throw new FinalExamAttemptInvalidStateException();
        }

        List<ExamQuestion> questions = examQuestionRepository
                .findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(finalExam.getId());
        Map<Long, ExamQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(ExamQuestion::getId, item -> item));

        Map<Long, ExamAttemptAnswer> existingAnswers = examAttemptAnswerRepository
                .findByAttemptIdAndQuestionIdIn(attempt.getId(), questionsById.keySet()).stream()
                .collect(Collectors.toMap(ExamAttemptAnswer::getQuestionId, item -> item));

        Instant answeredAt = Instant.now();
        List<ExamAttemptAnswer> toSave = new ArrayList<>();
        for (SaveExamAnswerRequest.AnswerItem answer : request.getAnswers()) {
            ExamQuestion question = questionsById.get(answer.getQuestionId());
            if (question == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }

            boolean optionBelongsToQuestion = question.getOptions().stream()
                    .anyMatch(option -> option.getId().equals(answer.getSelectedOptionId()));
            if (!optionBelongsToQuestion) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }

            ExamAttemptAnswer existing = existingAnswers.get(question.getId());
            if (existing == null) {
                existing = ExamAttemptAnswer.builder()
                        .attemptId(attempt.getId())
                        .questionId(question.getId())
                        .build();
            }
            existing.setSelectedOptionId(answer.getSelectedOptionId());
            existing.setAnsweredAt(answeredAt);
            toSave.add(existing);
        }

        examAttemptAnswerRepository.saveAll(toSave);
        return buildStudentAttemptResponse(attempt, finalExam, true);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#authContext)")
    public SubmitExamResponse submitAttempt(AuthContext authContext, String courseId, Long attemptId, SubmitExamRequest request) {
        String studentId = requireStudentId(authContext);
        ExamAttempt attempt = resolveOwnedAttempt(studentId, courseId, attemptId);
        FinalExam finalExam = finalExamRepository.findById(attempt.getFinalExamId()).orElseThrow(FinalExamNotFoundException::new);

        if (expireIfNeeded(attempt, finalExam, Instant.now())) {
            throw new FinalExamAttemptInvalidStateException();
        }
        if (attempt.getAttemptStatus() == AttemptStatus.SUBMITTED) {
            return toSubmitResponse(attempt, finalExam, studentId);
        }
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            throw new FinalExamAttemptInvalidStateException();
        }

        List<ExamQuestion> questions = examQuestionRepository
                .findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(finalExam.getId());
        Map<Long, ExamQuestion> questionById = questions.stream()
                .collect(Collectors.toMap(ExamQuestion::getId, item -> item));
        List<ExamAttemptAnswer> answers = examAttemptAnswerRepository
                .findByAttemptIdAndQuestionIdIn(attempt.getId(), questionById.keySet());
        Map<Long, Long> selectedOptionByQuestion = answers.stream()
                .collect(Collectors.toMap(ExamAttemptAnswer::getQuestionId, ExamAttemptAnswer::getSelectedOptionId, (left, right) -> right));

        int totalQuestions = questionById.size();
        int correctAnswers = 0;
        for (ExamQuestion question : questions) {
            Long selectedOptionId = selectedOptionByQuestion.get(question.getId());
            if (selectedOptionId == null) {
                continue;
            }
            boolean correct = question.getOptions().stream()
                    .anyMatch(option -> option.getId().equals(selectedOptionId) && option.isCorrect());
            if (correct) {
                correctAnswers++;
            }
        }

        BigDecimal score = totalQuestions == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(correctAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        boolean passed = score.compareTo(finalExam.getPassingScore()) >= 0;
        attempt.setAttemptStatus(AttemptStatus.SUBMITTED);
        attempt.setResultStatus(passed ? ResultStatus.PASSED : ResultStatus.FAILED);
        attempt.setPassed(passed);
        attempt.setScore(score);
        attempt.setSubmittedAt(Instant.now());
        attempt.setUpdatedBy(studentId);
        examAttemptRepository.save(attempt);

        if (passed) {
            ensureCertificateEligibility(attempt, studentId);
        }
        return toSubmitResponse(attempt, finalExam, studentId);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#authContext)")
    public SubmitExamResponse terminateAttempt(AuthContext authContext, String courseId, Long attemptId, String reason) {
        String studentId = requireStudentId(authContext);
        ExamAttempt attempt = resolveOwnedAttempt(studentId, courseId, attemptId);
        FinalExam finalExam = finalExamRepository.findById(attempt.getFinalExamId()).orElseThrow(FinalExamNotFoundException::new);
        Instant now = Instant.now();

        log.info(
                "Final exam terminate requested reason={} userId={} attemptId={} courseId={} currentStatus={} startedAt={} now={}",
                normalizeTerminateReason(reason),
                studentId,
                attemptId,
                courseId,
                attempt.getAttemptStatus(),
                attempt.getStartedAt(),
                now
        );

        if (!expireIfNeeded(attempt, finalExam, now) && attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS) {
            attempt.setAttemptStatus(AttemptStatus.TERMINATED);
            attempt.setTerminatedAt(now);
            attempt.setUpdatedBy(studentId);
            examAttemptRepository.save(attempt);
        }
        return toSubmitResponse(attempt, finalExam, studentId);
    }

    private SubmitExamResponse toSubmitResponse(ExamAttempt attempt, FinalExam finalExam, String studentId) {
        int consumedAttempts = countConsumedAttempts(finalExam.getId(), studentId);
        int remainingAttempts = Math.max(0, finalExam.getMaxAttempts() - consumedAttempts);
        boolean certificateEligible = certificateEligibilityRepository.existsByCourseIdAndStudentIdAndEligibleTrue(
                attempt.getCourseId(), studentId
        );

        return SubmitExamResponse.builder()
                .attemptId(attempt.getId())
                .attemptStatus(attempt.getAttemptStatus())
                .resultStatus(attempt.getResultStatus())
                .score(attempt.getScore())
                .passed(attempt.isPassed())
                .remainingAttempts(remainingAttempts)
                .certificateEligible(certificateEligible)
                .build();
    }

    private void ensureCertificateEligibility(ExamAttempt attempt, String studentId) {
        certificateEligibilityRepository.findByCourseIdAndStudentId(attempt.getCourseId(), studentId)
                .ifPresentOrElse(existing -> {
                    if (!existing.isEligible()) {
                        existing.setEligible(true);
                        existing.setAttemptId(attempt.getId());
                        existing.setFinalExamId(attempt.getFinalExamId());
                        existing.setEarnedAt(Instant.now());
                        existing.setUpdatedBy(studentId);
                        certificateEligibilityRepository.save(existing);
                    }
                }, () -> certificateEligibilityRepository.save(CertificateEligibility.builder()
                        .courseId(attempt.getCourseId())
                        .studentId(studentId)
                        .finalExamId(attempt.getFinalExamId())
                        .attemptId(attempt.getId())
                        .eligible(true)
                        .earnedAt(Instant.now())
                        .createdBy(studentId)
                        .updatedBy(studentId)
                        .build()));
    }

    private StudentExamAttemptResponse buildStudentAttemptResponse(ExamAttempt attempt, FinalExam finalExam, boolean includeQuestions) {
        List<StudentExamAttemptResponse.StudentQuestion> mappedQuestions = List.of();
        if (includeQuestions) {
            List<ExamQuestion> questions = examQuestionRepository
                    .findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(finalExam.getId());
            Map<Long, Long> selectedOptionByQuestion = examAttemptAnswerRepository.findByAttemptId(attempt.getId()).stream()
                    .collect(Collectors.toMap(ExamAttemptAnswer::getQuestionId, ExamAttemptAnswer::getSelectedOptionId, (left, right) -> right));

            mappedQuestions = questions.stream()
                    .sorted(Comparator.comparing(ExamQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ExamQuestion::getId))
                    .map(question -> StudentExamAttemptResponse.StudentQuestion.builder()
                            .questionId(question.getId())
                            .questionText(question.getQuestionText())
                            .imageUrl(question.getImageUrl())
                            .orderIndex(question.getOrderIndex())
                            .selectedOptionId(selectedOptionByQuestion.get(question.getId()))
                            .options(question.getOptions().stream()
                                    .sorted(Comparator.comparing(ExamOption::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                                            .thenComparing(ExamOption::getId))
                                    .map(option -> StudentExamAttemptResponse.StudentOption.builder()
                                            .optionId(option.getId())
                                            .optionText(option.getOptionText())
                                            .orderIndex(option.getOrderIndex())
                                            .build())
                                    .toList())
                            .build())
                    .toList();
        }

        Instant expiresAt = computeExpiryTime(attempt, finalExam);
        long remainingSeconds = Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());

        return StudentExamAttemptResponse.builder()
                .attemptId(attempt.getId())
                .finalExamId(attempt.getFinalExamId())
                .courseId(attempt.getCourseId())
                .attemptNumber(attempt.getAttemptNumber())
                .attemptStatus(attempt.getAttemptStatus())
                .resultStatus(attempt.getResultStatus())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .terminatedAt(attempt.getTerminatedAt())
                .expiredAt(attempt.getExpiredAt())
                .expiresAt(expiresAt)
                .remainingDurationSeconds(remainingSeconds)
                .statusMessage(resolveAttemptStatusMessage(attempt.getAttemptStatus()))
                .questions(mappedQuestions)
                .build();
    }

    private String resolveCanStartReason(
            FinalExam finalExam,
            boolean courseCompleted,
            ExamAttempt activeAttempt,
            int remainingAttempts
    ) {
        if (!finalExam.isActive()) {
            return "Final sınav şu anda aktif değil.";
        }
        if (!courseCompleted) {
            return "Final sınavına başlayabilmek için kurs videolarını tamamlamalısınız.";
        }
        if (activeAttempt != null) {
            return "Devam eden bir sınav oturumunuz var. Mevcut oturuma devam edebilirsiniz.";
        }
        if (remainingAttempts <= 0) {
            return "Final sınav deneme hakkınız tükendi.";
        }
        return "Final sınava başlayabilirsiniz.";
    }

    private ProgressSnapshot computeProgressSnapshot(String studentId, Course course) {
        Set<String> requiredVideoLessonIds = extractRequiredVideoLessonIds(course.getLessons());
        if (requiredVideoLessonIds.isEmpty()) {
            return new ProgressSnapshot(0, 0, ONE_HUNDRED.setScale(2, RoundingMode.HALF_UP));
        }

        Map<String, LessonProgress> progressByLessonId = lessonProgressRepository
                .findByUserIdAndCourseId(studentId, course.getId()).stream()
                .filter(progress -> progress.getLessonId() != null && !progress.getLessonId().isBlank())
                .collect(Collectors.toMap(
                        LessonProgress::getLessonId,
                        item -> item,
                        this::selectMostCompleteProgress
                ));

        BigDecimal threshold = effectiveCompletionThreshold();
        int completedVideos = 0;
        for (String lessonId : requiredVideoLessonIds) {
            LessonProgress progress = progressByLessonId.get(lessonId);
            if (isLessonCompleted(progress, threshold)) {
                completedVideos++;
            }
        }

        BigDecimal completionPercentage = BigDecimal.valueOf(completedVideos)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(requiredVideoLessonIds.size()), 2, RoundingMode.HALF_UP);
        return new ProgressSnapshot(requiredVideoLessonIds.size(), completedVideos, completionPercentage);
    }

    private Set<String> extractRequiredVideoLessonIds(List<Lesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return Set.of();
        }
        return lessons.stream()
                .filter(lesson -> lesson != null
                        && lesson.getId() != null
                        && !lesson.getId().isBlank()
                        && lesson.getVideoUrl() != null
                        && !lesson.getVideoUrl().isBlank())
                .map(Lesson::getId)
                .collect(Collectors.toSet());
    }

    private LessonProgress selectMostCompleteProgress(LessonProgress left, LessonProgress right) {
        return completionScore(right) > completionScore(left) ? right : left;
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

    private AttemptSummaryResponse toAttemptSummary(ExamAttempt attempt) {
        return AttemptSummaryResponse.builder()
                .attemptId(attempt.getId())
                .attemptNumber(attempt.getAttemptNumber())
                .attemptStatus(attempt.getAttemptStatus())
                .resultStatus(attempt.getResultStatus())
                .score(attempt.getScore())
                .passed(attempt.isPassed())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .terminatedAt(attempt.getTerminatedAt())
                .expiredAt(attempt.getExpiredAt())
                .build();
    }

    private Course resolvePublishedCourseForStudent(String studentId, String courseId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new CourseNotFoundException();
        }

        boolean enrolled = enrollmentAccessClient.hasActiveEnrollment(studentId, course.getId());
        if (!enrolled) {
            throw new AccessDeniedException("Enrollment required");
        }
        return course;
    }

    private String requireStudentId(AuthContext authContext) {
        if (authContext == null || authContext.userId() == null || authContext.userId().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        if (authContext.role() == null || authContext.role() == UserRole.UNKNOWN) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return authContext.userId().trim();
    }

    private ExamAttempt resolveOwnedAttempt(String studentId, String courseId, Long attemptId) {
        ExamAttempt attempt = examAttemptRepository.findByIdAndCourseId(attemptId, courseId).orElse(null);
        if (attempt == null) {
            ExamAttempt byId = examAttemptRepository.findById(attemptId).orElse(null);
            if (byId == null) {
                log.warn(
                        "Final exam attempt not found userId={} courseId={} attemptId={} reason=ATTEMPT_ID_NOT_FOUND",
                        studentId,
                        courseId,
                        attemptId
                );
            } else if (!courseId.equals(byId.getCourseId())) {
                log.warn(
                        "Final exam attempt not found userId={} courseId={} attemptId={} reason=COURSE_ID_MISMATCH actualCourseId={} finalExamId={} ownerUserId={}",
                        studentId,
                        courseId,
                        attemptId,
                        byId.getCourseId(),
                        byId.getFinalExamId(),
                        byId.getStudentId()
                );
            } else if (!studentId.equals(byId.getStudentId())) {
                log.warn(
                        "Final exam attempt not found userId={} courseId={} attemptId={} reason=OWNERSHIP_MISMATCH actualOwnerUserId={} finalExamId={}",
                        studentId,
                        courseId,
                        attemptId,
                        byId.getStudentId(),
                        byId.getFinalExamId()
                );
            } else {
                log.warn(
                        "Final exam attempt not found userId={} courseId={} attemptId={} reason=UNEXPECTED_SCOPED_LOOKUP_MISS finalExamId={}",
                        studentId,
                        courseId,
                        attemptId,
                        byId.getFinalExamId()
                );
            }
            throw new FinalExamAttemptNotFoundException();
        }
        if (!attempt.getStudentId().equals(studentId)) {
            log.warn(
                    "Final exam attempt access denied userId={} courseId={} attemptId={} reason=OWNERSHIP_MISMATCH actualOwnerUserId={} finalExamId={}",
                    studentId,
                    courseId,
                    attemptId,
                    attempt.getStudentId(),
                    attempt.getFinalExamId()
            );
            throw new AccessDeniedException("Attempt does not belong to current user");
        }
        return attempt;
    }

    private void validateExamReadyForStudents(FinalExam finalExam) {
        List<ExamQuestion> questions = examQuestionRepository
                .findByFinalExamIdAndActiveTrueOrderByOrderIndexAscIdAsc(finalExam.getId());
        if (questions.size() != finalExam.getQuestionCount()) {
            throw new FinalExamNotReadyException();
        }
        for (ExamQuestion question : questions) {
            long correctCount = question.getOptions().stream().filter(ExamOption::isCorrect).count();
            if (question.getOptions().size() < 2 || correctCount != 1) {
                throw new FinalExamNotReadyException();
            }
        }
    }

    private int countConsumedAttempts(Long finalExamId, String studentId) {
        return (int) examAttemptRepository.countByFinalExamIdAndStudentIdAndAttemptStatusIn(
                finalExamId,
                studentId,
                CONSUMING_STATUSES
        );
    }

    private int countConsumedAttempts(List<ExamAttempt> attempts) {
        int count = 0;
        for (ExamAttempt attempt : attempts) {
            if (CONSUMING_STATUSES.contains(attempt.getAttemptStatus())) {
                count++;
            }
        }
        return count;
    }

    private List<ExamAttempt> normalizeInProgressAttemptsIfNeeded(
            List<ExamAttempt> attempts,
            FinalExam finalExam,
            String expectedCourseId,
            String studentId,
            Instant now
    ) {
        if (attempts == null || attempts.isEmpty()) {
            return List.of();
        }
        List<ExamAttempt> updated = new ArrayList<>(attempts.size());
        for (ExamAttempt attempt : attempts) {
            if (attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS) {
                normalizeCandidateActiveAttempt(attempt, finalExam, expectedCourseId, studentId, now);
            }
            updated.add(attempt);
        }
        return updated;
    }

    private ExamAttempt normalizeCandidateActiveAttempt(
            ExamAttempt attempt,
            FinalExam finalExam,
            String expectedCourseId,
            String studentId,
            Instant now
    ) {
        if (attempt == null) {
            return null;
        }
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            return null;
        }
        if (!studentId.equals(attempt.getStudentId())) {
            return null;
        }
        if (!finalExam.getId().equals(attempt.getFinalExamId()) || !expectedCourseId.equals(attempt.getCourseId())) {
            terminateInconsistentInProgressAttempt(attempt, now);
            return null;
        }
        if (expireIfNeeded(attempt, finalExam, now)) {
            return null;
        }
        return attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS ? attempt : null;
    }

    private boolean isContinuableInProgressAttempt(ExamAttempt attempt, FinalExam finalExam, String expectedCourseId) {
        return attempt != null
                && attempt.getAttemptStatus() == AttemptStatus.IN_PROGRESS
                && finalExam.getId().equals(attempt.getFinalExamId())
                && expectedCourseId.equals(attempt.getCourseId());
    }

    private void terminateInconsistentInProgressAttempt(ExamAttempt attempt, Instant now) {
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            return;
        }
        attempt.setAttemptStatus(AttemptStatus.TERMINATED);
        if (attempt.getTerminatedAt() == null) {
            attempt.setTerminatedAt(now);
        }
        attempt.setUpdatedBy("system");
        examAttemptRepository.save(attempt);
    }

    private boolean expireIfNeeded(ExamAttempt attempt, FinalExam finalExam, Instant now) {
        if (attempt.getAttemptStatus() != AttemptStatus.IN_PROGRESS) {
            return false;
        }

        Instant expiresAt = computeExpiryTime(attempt, finalExam);
        if (now.isBefore(expiresAt)) {
            return false;
        }

        attempt.setAttemptStatus(AttemptStatus.EXPIRED);
        attempt.setExpiredAt(now);
        attempt.setUpdatedBy("system");
        examAttemptRepository.save(attempt);
        return true;
    }

    private String resolveAttemptStatusMessage(AttemptStatus attemptStatus) {
        if (attemptStatus == null) {
            return null;
        }
        return switch (attemptStatus) {
            case EXPIRED -> "Sinav oturumunuzun suresi doldu.";
            case TERMINATED -> "Bu sinav oturumu sonlandirildi.";
            default -> null;
        };
    }

    private Instant computeExpiryTime(ExamAttempt attempt, FinalExam finalExam) {
        Instant startedAt = attempt.getStartedAt();
        Instant byDuration = startedAt.plusSeconds((long) finalExam.getDurationMinutes() * 60L);
        Integer availabilityDays = finalExam.getAvailabilityDays();
        if (availabilityDays == null || availabilityDays <= 0) {
            return byDuration;
        }
        Instant byAvailability = startedAt.plusSeconds((long) availabilityDays * 24L * 60L * 60L);
        return byDuration.isBefore(byAvailability) ? byDuration : byAvailability;
    }

    private String normalizeTerminateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }
        String trimmed = reason.trim();
        return trimmed.length() > 256 ? trimmed.substring(0, 256) : trimmed;
    }

    private record ProgressSnapshot(int totalVideos, int completedVideos, BigDecimal completionPercentage) {
    }
}
