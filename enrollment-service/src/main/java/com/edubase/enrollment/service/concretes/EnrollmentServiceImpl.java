package com.edubase.enrollment.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.enrollment.configuration.mapper.EnrollmentMapper;
import com.edubase.enrollment.dto.request.EnrollmentCreateRequest;
import com.edubase.enrollment.dto.response.CustomPageResponse;
import com.edubase.enrollment.dto.response.EnrollmentResponse;
import com.edubase.enrollment.entity.Enrollment;
import com.edubase.enrollment.entity.EnrollmentStatus;
import com.edubase.enrollment.exception.EnrollmentAlreadyExistsException;
import com.edubase.enrollment.exception.EnrollmentNotFoundException;
import com.edubase.enrollment.grpc.CourseGrpcClient;
import com.edubase.enrollment.grpc.CourseSummary;
import com.edubase.enrollment.grpc.PaymentGrpcClient;
import com.edubase.enrollment.grpc.UserGrpcClient;
import com.edubase.enrollment.messaging.EnrollmentCancelledDomainEvent;
import com.edubase.enrollment.messaging.EnrollmentCreatedDomainEvent;
import com.edubase.enrollment.repository.EnrollmentRepository;
import com.edubase.enrollment.security.AuthContext;
import com.edubase.enrollment.security.UserRole;
import com.edubase.enrollment.service.abstracts.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMapper enrollmentMapper;
    private final UserGrpcClient userGrpcClient;
    private final CourseGrpcClient courseGrpcClient;
    private final PaymentGrpcClient paymentGrpcClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    @PreAuthorize("@enrollmentSecurity.canCreateEnrollment(#authContext, #request)")
    public EnrollmentResponse createEnrollment(AuthContext authContext, EnrollmentCreateRequest request) {
        requireAuthenticatedRole(authContext);
        Long actorUserId = requireUserId(authContext);
        Long targetUserId = request.getUserId() != null ? request.getUserId() : actorUserId;
        if (!isAdmin(authContext) && !actorUserId.equals(targetUserId)) {
            throw new AccessDeniedException("Cannot create enrollment for another user");
        }

        String courseId = normalizeCourseId(request.getCourseId());
        userGrpcClient.assertUserExists(targetUserId);
        CourseSummary courseSummary = courseGrpcClient.getPublishedCourse(courseId);
        if (courseSummary.price() != null && courseSummary.price().compareTo(BigDecimal.ZERO) > 0) {
            paymentGrpcClient.assertSuccessfulPayment(targetUserId, courseId);
        }
        Optional<Enrollment> existing = enrollmentRepository.findByUserIdAndCourseId(targetUserId, courseId);
        if (existing.isPresent()) {
            Enrollment enrollment = existing.get();
            if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
                enrollment.setStatus(EnrollmentStatus.ACTIVE);
                Enrollment saved = enrollmentRepository.save(enrollment);
                applicationEventPublisher.publishEvent(new EnrollmentCreatedDomainEvent(saved.getId(), saved.getUserId(), saved.getCourseId()));
                return enrollmentMapper.toResponseFromEntity(saved);
            }
            throw new EnrollmentAlreadyExistsException();
        }

        Enrollment enrollment = enrollmentMapper.toEntityFromRequest(request);
        enrollment.setUserId(targetUserId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);

        try {
            Enrollment saved = enrollmentRepository.save(enrollment);
            applicationEventPublisher.publishEvent(new EnrollmentCreatedDomainEvent(saved.getId(), saved.getUserId(), saved.getCourseId()));
            return enrollmentMapper.toResponseFromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new EnrollmentAlreadyExistsException();
        }
    }

    @Override
    @PreAuthorize("@enrollmentSecurity.canAccessEnrollment(#authContext, #id)")
    public EnrollmentResponse getEnrollmentById(AuthContext authContext, Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(EnrollmentNotFoundException::new);
        return enrollmentMapper.toResponseFromEntity(enrollment);
    }

    @Override
    @PreAuthorize("@enrollmentSecurity.isAuthenticatedUser(#authContext)")
    public CustomPageResponse<EnrollmentResponse> getMyEnrollments(AuthContext authContext, int pageNumber, int pageSize) {
        requireAuthenticatedRole(authContext);
        Long userId = requireUserId(authContext);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Enrollment> page = enrollmentRepository.findAllByUserId(userId, pageRequest);
        List<EnrollmentResponse> responses = page.getContent().stream()
                .map(enrollmentMapper::toResponseFromEntity)
                .toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @PreAuthorize("@enrollmentSecurity.isAdmin(#authContext)")
    public CustomPageResponse<EnrollmentResponse> getEnrollmentsByCourse(AuthContext authContext, String courseId, int pageNumber, int pageSize) {
        String normalizedCourseId = normalizeCourseId(courseId);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Enrollment> page = enrollmentRepository.findAllByCourseId(normalizedCourseId, pageRequest);
        List<EnrollmentResponse> responses = page.getContent().stream()
                .map(enrollmentMapper::toResponseFromEntity)
                .toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @Transactional
    @PreAuthorize("@enrollmentSecurity.canAccessEnrollment(#authContext, #id)")
    public void cancelEnrollment(AuthContext authContext, Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(EnrollmentNotFoundException::new);
        if (enrollment.getStatus() != EnrollmentStatus.CANCELLED) {
            enrollment.setStatus(EnrollmentStatus.CANCELLED);
            enrollmentRepository.save(enrollment);
            applicationEventPublisher.publishEvent(new EnrollmentCancelledDomainEvent(
                    enrollment.getId(),
                    enrollment.getUserId(),
                    enrollment.getCourseId()
            ));
        }
    }

    private void requireAuthenticatedRole(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            throw new AccessDeniedException("Role required");
        }
    }

    private Long requireUserId(AuthContext authContext) {
        if (authContext == null || authContext.userId() == null || authContext.userId().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        try {
            return Long.parseLong(authContext.userId().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private boolean isAdmin(AuthContext authContext) {
        return authContext != null && authContext.role() == UserRole.ADMIN;
    }

    private String normalizeCourseId(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return courseId.trim();
    }
}
