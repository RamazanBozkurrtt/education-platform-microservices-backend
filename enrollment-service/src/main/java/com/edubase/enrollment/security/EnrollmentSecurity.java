package com.edubase.enrollment.security;

import com.edubase.enrollment.repository.EnrollmentRepository;
import com.edubase.enrollment.dto.request.EnrollmentCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentSecurity {

    private final EnrollmentRepository enrollmentRepository;

    public boolean isAdmin(AuthContext authContext) {
        return authContext != null && authContext.role() == UserRole.ADMIN;
    }

    public boolean canAccessEnrollment(AuthContext authContext, Long enrollmentId) {
        if (authContext == null || enrollmentId == null) {
            return false;
        }
        if (authContext.role() == UserRole.ADMIN) {
            return true;
        }
        Long userId = parseUserId(authContext.userId());
        if (userId == null) {
            return false;
        }
        return enrollmentRepository.existsByIdAndUserId(enrollmentId, userId);
    }

    public boolean canCreateForUser(AuthContext authContext, Long userId) {
        if (authContext == null || userId == null) {
            return false;
        }
        if (authContext.role() == UserRole.ADMIN) {
            return true;
        }
        Long authUserId = parseUserId(authContext.userId());
        return authUserId != null && authUserId.equals(userId);
    }

    public boolean canCreateEnrollment(AuthContext authContext, EnrollmentCreateRequest request) {
        if (authContext == null || request == null) {
            return false;
        }
        if (authContext.role() == UserRole.ADMIN) {
            return true;
        }
        Long authUserId = parseUserId(authContext.userId());
        if (authUserId == null) {
            return false;
        }
        Long requestUserId = request.getUserId();
        if (requestUserId == null) {
            return true;
        }
        return authUserId.equals(requestUserId);
    }

    public boolean isAuthenticatedUser(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            return false;
        }
        return parseUserId(authContext.userId()) != null;
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
