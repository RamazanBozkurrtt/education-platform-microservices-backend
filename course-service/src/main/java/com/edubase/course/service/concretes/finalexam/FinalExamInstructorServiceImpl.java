package com.edubase.course.service.concretes.finalexam;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.dto.request.finalexam.ExamOptionRequest;
import com.edubase.course.dto.request.finalexam.ExamQuestionCreateRequest;
import com.edubase.course.dto.request.finalexam.ExamQuestionUpdateRequest;
import com.edubase.course.dto.request.finalexam.FinalExamCreateRequest;
import com.edubase.course.dto.request.finalexam.FinalExamUpdateRequest;
import com.edubase.course.dto.response.finalexam.FinalExamManageResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.finalexam.ExamOption;
import com.edubase.course.entity.finalexam.ExamQuestion;
import com.edubase.course.entity.finalexam.FinalExam;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.FinalExamAlreadyExistsException;
import com.edubase.course.exception.FinalExamNotFoundException;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.repository.finalexam.ExamQuestionRepository;
import com.edubase.course.repository.finalexam.FinalExamRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.finalexam.FinalExamInstructorService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FinalExamInstructorServiceImpl implements FinalExamInstructorService {

    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "svg");
    private static final List<String> SUPPORTED_IMAGE_CONTENT_TYPES = List.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/svg+xml"
    );
    private static final String QUESTION_IMAGE_PATH_TEMPLATE = "/api/v1/courses/%s/final-exam/questions/%d/image";

    private final CourseRepository courseRepository;
    private final FinalExamRepository finalExamRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final MinioClient minioClient;

    @Value("${course.media.minio.bucket:edubase-media}")
    private String mediaBucket;

    @Value("${course.media.minio.auto-create-bucket:true}")
    private boolean autoCreateBucket;

    @Value("${course.media.exam-image-base-path:images/final-exams}")
    private String examImageBasePath;

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public FinalExamManageResponse createFinalExam(AuthContext authContext, String courseId, FinalExamCreateRequest request) {
        Course course = resolveManagedCourse(authContext, courseId);
        if (finalExamRepository.existsByCourseIdAndActiveTrue(course.getId())) {
            throw new FinalExamAlreadyExistsException();
        }

        FinalExam finalExam = FinalExam.builder()
                .courseId(course.getId())
                .title(request.getTitle().trim())
                .description(normalizeText(request.getDescription()))
                .passingScore(normalizeScore(request.getPassingScore()))
                .questionCount(request.getQuestionCount())
                .durationMinutes(request.getDurationMinutes())
                .maxAttempts(defaultIfNull(request.getMaxAttempts(), 3))
                .availabilityDays(defaultIfNull(request.getAvailabilityDays(), 3))
                .active(request.getActive() == null || request.getActive())
                .createdBy(authContext.userId())
                .updatedBy(authContext.userId())
                .build();

        FinalExam saved = finalExamRepository.save(finalExam);
        return toManageResponse(saved, List.of());
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public FinalExamManageResponse updateFinalExam(AuthContext authContext, String courseId, FinalExamUpdateRequest request) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);

        finalExam.setTitle(request.getTitle().trim());
        finalExam.setDescription(normalizeText(request.getDescription()));
        finalExam.setPassingScore(normalizeScore(request.getPassingScore()));
        finalExam.setQuestionCount(request.getQuestionCount());
        finalExam.setDurationMinutes(request.getDurationMinutes());
        finalExam.setMaxAttempts(request.getMaxAttempts());
        finalExam.setAvailabilityDays(request.getAvailabilityDays());
        finalExam.setActive(request.getActive());
        finalExam.setUpdatedBy(authContext.userId());

        long activeQuestionCount = examQuestionRepository.countByFinalExamIdAndActiveTrue(finalExam.getId());
        if (activeQuestionCount > finalExam.getQuestionCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        FinalExam saved = finalExamRepository.save(finalExam);
        List<ExamQuestion> questions = examQuestionRepository.findByFinalExamIdOrderByOrderIndexAscIdAsc(saved.getId());
        return toManageResponse(saved, questions);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public FinalExamManageResponse getFinalExamForManage(AuthContext authContext, String courseId) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);
        List<ExamQuestion> questions = examQuestionRepository.findByFinalExamIdOrderByOrderIndexAscIdAsc(finalExam.getId());
        return toManageResponse(finalExam, questions);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public void deleteFinalExam(AuthContext authContext, String courseId) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);
        finalExam.setActive(false);
        finalExam.setUpdatedBy(authContext.userId());
        finalExamRepository.save(finalExam);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public FinalExamManageResponse addQuestion(AuthContext authContext, String courseId, ExamQuestionCreateRequest request) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);

        validateOptions(request.getOptions());
        enforceQuestionCapacity(finalExam, request.getActive(), null);

        ExamQuestion question = ExamQuestion.builder()
                .finalExam(finalExam)
                .questionText(request.getQuestionText().trim())
                .orderIndex(request.getOrderIndex())
                .points(normalizePoints(request.getPoints()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .createdBy(authContext.userId())
                .updatedBy(authContext.userId())
                .options(new ArrayList<>())
                .build();

        attachOptions(question, request.getOptions(), authContext.userId());
        examQuestionRepository.save(question);

        List<ExamQuestion> questions = examQuestionRepository.findByFinalExamIdOrderByOrderIndexAscIdAsc(finalExam.getId());
        return toManageResponse(finalExam, questions);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public FinalExamManageResponse updateQuestion(
            AuthContext authContext,
            String courseId,
            Long questionId,
            ExamQuestionUpdateRequest request
    ) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);
        ExamQuestion question = examQuestionRepository.findByIdAndFinalExamId(questionId, finalExam.getId())
                .orElseThrow(FinalExamNotFoundException::new);

        validateOptions(request.getOptions());
        enforceQuestionCapacity(finalExam, request.getActive(), question);

        question.setQuestionText(request.getQuestionText().trim());
        question.setOrderIndex(request.getOrderIndex());
        question.setPoints(normalizePoints(request.getPoints()));
        question.setActive(Boolean.TRUE.equals(request.getActive()));
        question.setUpdatedBy(authContext.userId());

        question.getOptions().clear();
        attachOptions(question, request.getOptions(), authContext.userId());
        examQuestionRepository.save(question);

        List<ExamQuestion> questions = examQuestionRepository.findByFinalExamIdOrderByOrderIndexAscIdAsc(finalExam.getId());
        return toManageResponse(finalExam, questions);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public FinalExamManageResponse deleteQuestion(AuthContext authContext, String courseId, Long questionId) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);
        ExamQuestion question = examQuestionRepository.findByIdAndFinalExamId(questionId, finalExam.getId())
                .orElseThrow(FinalExamNotFoundException::new);

        removeQuestionImages(courseId, question.getId());
        examQuestionRepository.delete(question);

        List<ExamQuestion> questions = examQuestionRepository.findByFinalExamIdOrderByOrderIndexAscIdAsc(finalExam.getId());
        return toManageResponse(finalExam, questions);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public void uploadQuestionImage(AuthContext authContext, String courseId, Long questionId, MultipartFile file) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);
        ExamQuestion question = examQuestionRepository.findByIdAndFinalExamId(questionId, finalExam.getId())
                .orElseThrow(FinalExamNotFoundException::new);

        validateImageFile(file);
        String extension = resolveImageExtension(file);
        ensureBucketForWrites();
        removeQuestionImages(courseId, questionId);

        String objectKey = buildQuestionImageObjectKey(courseId, questionId, extension);
        putObject(objectKey, file, normalizeImageContentType(file, extension));
        question.setImageObjectKey(objectKey);
        question.setImageUrl(QUESTION_IMAGE_PATH_TEMPLATE.formatted(courseId, questionId));
        question.setUpdatedBy(authContext.userId());
        examQuestionRepository.save(question);
    }

    @Override
    @Transactional
    @PreAuthorize("@courseSecurity.canManageCourse(#authContext, #courseId)")
    public void deleteQuestionImage(AuthContext authContext, String courseId, Long questionId) {
        resolveManagedCourse(authContext, courseId);
        FinalExam finalExam = finalExamRepository.findByCourseIdAndActiveTrue(courseId).orElseThrow(FinalExamNotFoundException::new);
        ExamQuestion question = examQuestionRepository.findByIdAndFinalExamId(questionId, finalExam.getId())
                .orElseThrow(FinalExamNotFoundException::new);

        ensureBucketForWrites();
        removeQuestionImages(courseId, questionId);
        question.setImageObjectKey(null);
        question.setImageUrl(null);
        question.setUpdatedBy(authContext.userId());
        examQuestionRepository.save(question);
    }

    private Course resolveManagedCourse(AuthContext authContext, String courseId) {
        if (authContext == null || authContext.userId() == null || authContext.userId().isBlank()) {
            throw new CourseNotFoundException();
        }
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
        if (authContext.role() == UserRole.ADMIN) {
            return course;
        }
        if (authContext.role() != UserRole.INSTRUCTOR || !authContext.userId().equals(course.getInstructorId())) {
            throw new CourseNotFoundException();
        }
        return course;
    }

    private FinalExamManageResponse toManageResponse(FinalExam finalExam, List<ExamQuestion> questions) {
        List<FinalExamManageResponse.ManageQuestion> mappedQuestions = questions.stream()
                .sorted(Comparator.comparing(ExamQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ExamQuestion::getId))
                .map(question -> FinalExamManageResponse.ManageQuestion.builder()
                        .questionId(question.getId())
                        .questionText(question.getQuestionText())
                        .imageUrl(question.getImageUrl())
                        .imageObjectKey(question.getImageObjectKey())
                        .orderIndex(question.getOrderIndex())
                        .points(question.getPoints())
                        .active(question.isActive())
                        .options(question.getOptions().stream()
                                .sorted(Comparator.comparing(ExamOption::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                                        .thenComparing(ExamOption::getId))
                                .map(option -> FinalExamManageResponse.ManageOption.builder()
                                        .optionId(option.getId())
                                        .optionText(option.getOptionText())
                                        .correct(option.isCorrect())
                                        .orderIndex(option.getOrderIndex())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return FinalExamManageResponse.builder()
                .finalExamId(finalExam.getId())
                .courseId(finalExam.getCourseId())
                .title(finalExam.getTitle())
                .description(finalExam.getDescription())
                .passingScore(finalExam.getPassingScore())
                .questionCount(finalExam.getQuestionCount())
                .durationMinutes(finalExam.getDurationMinutes())
                .maxAttempts(finalExam.getMaxAttempts())
                .availabilityDays(finalExam.getAvailabilityDays())
                .active(finalExam.isActive())
                .createdBy(finalExam.getCreatedBy())
                .updatedBy(finalExam.getUpdatedBy())
                .createdAt(finalExam.getCreatedAt())
                .updatedAt(finalExam.getUpdatedAt())
                .questions(mappedQuestions)
                .build();
    }

    private void validateOptions(List<ExamOptionRequest> options) {
        if (options == null || options.size() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        long correctCount = options.stream()
                .filter(option -> option.getIsCorrect() != null && option.getIsCorrect())
                .count();
        if (correctCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void enforceQuestionCapacity(FinalExam finalExam, Boolean newActive, ExamQuestion existingQuestion) {
        boolean requestedActive = Boolean.TRUE.equals(newActive);
        if (!requestedActive) {
            return;
        }

        long activeCount = examQuestionRepository.countByFinalExamIdAndActiveTrue(finalExam.getId());
        if (existingQuestion != null && existingQuestion.isActive()) {
            return;
        }
        if (activeCount >= finalExam.getQuestionCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void attachOptions(ExamQuestion question, List<ExamOptionRequest> options, String actorUserId) {
        for (ExamOptionRequest optionRequest : options) {
            ExamOption option = ExamOption.builder()
                    .question(question)
                    .optionText(optionRequest.getOptionText().trim())
                    .correct(Boolean.TRUE.equals(optionRequest.getIsCorrect()))
                    .orderIndex(optionRequest.getOrderIndex())
                    .createdBy(actorUserId)
                    .updatedBy(actorUserId)
                    .build();
            question.getOptions().add(option);
        }
    }

    private BigDecimal normalizePoints(BigDecimal points) {
        if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return points;
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return score;
    }

    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        String extension = resolveImageExtension(file);
        if (extension == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String resolveImageExtension(MultipartFile file) {
        String extensionFromName = extensionOf(file.getOriginalFilename());
        if (SUPPORTED_IMAGE_EXTENSIONS.contains(extensionFromName)) {
            return extensionFromName;
        }

        String contentType = normalizeContentType(file.getContentType());
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> null;
        };
    }

    private String normalizeImageContentType(MultipartFile file, String extension) {
        String normalizedContentType = normalizeContentType(file.getContentType());
        if (SUPPORTED_IMAGE_CONTENT_TYPES.contains(normalizedContentType)) {
            return normalizedContentType;
        }
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            return normalized.substring(0, separator).trim();
        }
        return normalized;
    }

    private void ensureBucketForWrites() {
        if (!autoCreateBucket) {
            return;
        }
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(mediaBucket).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(mediaBucket).build());
            }
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.COURSE_MEDIA_STORAGE_ERROR, ex);
        }
    }

    private void putObject(String objectKey, MultipartFile file, String contentType) {
        try (var inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(mediaBucket)
                    .object(objectKey)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.COURSE_MEDIA_STORAGE_ERROR, ex);
        }
    }

    private void removeQuestionImages(String courseId, Long questionId) {
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            removeObjectIfExists(buildQuestionImageObjectKey(courseId, questionId, extension));
        }
    }

    private void removeObjectIfExists(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(mediaBucket).object(objectKey).build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.COURSE_MEDIA_STORAGE_ERROR, ex);
        }
    }

    private String buildQuestionImageObjectKey(String courseId, Long questionId, String extension) {
        String normalizedBase = normalizePath(examImageBasePath);
        String fileName = "%s/%d.%s".formatted(courseId, questionId, extension);
        if (normalizedBase.isBlank()) {
            return fileName;
        }
        return "%s/%s".formatted(normalizedBase, fileName);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
