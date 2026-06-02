package com.edubase.enrollment.controller;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import com.edubase.enrollment.controller.base.RestBaseController;
import com.edubase.enrollment.dto.internal.EnrollmentAccessResponse;
import com.edubase.enrollment.dto.internal.EnrollmentCourseCountsRequest;
import com.edubase.enrollment.entity.EnrollmentStatus;
import com.edubase.enrollment.repository.EnrollmentRepository;
import com.edubase.enrollment.repository.projection.CourseEnrollmentCountProjection;
import com.edubase.enrollment.service.concretes.InternalApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @PostMapping("/course-counts")
    public ResponseEntity<RestResponse<Map<String, Long>>> countSuccessfulEnrollmentsByCourseIds(
            @RequestHeader(name = INTERNAL_API_KEY_HEADER, required = false) String apiKey,
            @RequestBody @Valid EnrollmentCourseCountsRequest request) {
        internalApiKeyService.validate(apiKey);

        LinkedHashSet<String> courseIds = normalizeCourseIds(request.getCourseIds());
        if (courseIds.isEmpty()) {
            return ok(Map.of());
        }

        Map<String, Long> counts = enrollmentRepository.countByCourseIdsAndStatuses(
                        courseIds,
                        List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)
                ).stream()
                .collect(Collectors.toMap(
                        CourseEnrollmentCountProjection::getCourseId,
                        row -> row.getEnrollmentCount() == null ? 0L : row.getEnrollmentCount()
                ));

        Map<String, Long> response = courseIds.stream()
                .collect(Collectors.toMap(
                        courseId -> courseId,
                        courseId -> counts.getOrDefault(courseId, 0L),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return ok(response);
    }

    private Long parseUserId(String userId) {
        try {
            long parsed = Long.parseLong(userId.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LinkedHashSet<String> normalizeCourseIds(List<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return courseIds.stream()
                .filter(courseId -> courseId != null && !courseId.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
