package com.edubase.enrollment.controller;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import com.edubase.enrollment.controller.base.RestBaseController;
import com.edubase.enrollment.dto.internal.EnrollmentAccessResponse;
import com.edubase.enrollment.entity.EnrollmentStatus;
import com.edubase.enrollment.repository.EnrollmentRepository;
import com.edubase.enrollment.service.concretes.InternalApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/enrollments")
public class EnrollmentInternalController extends RestBaseController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyService internalApiKeyService;
    private final EnrollmentRepository enrollmentRepository;

    @GetMapping("/access")
    public ResponseEntity<RestResponse<EnrollmentAccessResponse>> hasActiveEnrollment(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @RequestParam String userId,
            @RequestParam String courseId) {
        internalApiKeyService.validate(apiKey);

        if (userId == null || userId.isBlank() || courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Long parsedUserId = parseUserId(userId);
        if (parsedUserId == null) {
            return ok(EnrollmentAccessResponse.builder().enrolled(false).build());
        }

        boolean enrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
                parsedUserId,
                courseId.trim(),
                EnrollmentStatus.ACTIVE
        );
        return ok(EnrollmentAccessResponse.builder().enrolled(enrolled).build());
    }

    private Long parseUserId(String userId) {
        try {
            long parsed = Long.parseLong(userId.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
