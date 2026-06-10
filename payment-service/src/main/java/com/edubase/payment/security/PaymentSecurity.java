package com.edubase.payment.security;

import com.edubase.payment.dto.request.PaymentCreateRequest;
import com.edubase.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSecurity {

    private final PaymentRepository paymentRepository;

    public boolean isAdmin(AuthContext authContext) {
        return authContext != null && authContext.role() == UserRole.ADMIN;
    }

    public boolean isAuthenticatedUser(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            return false;
        }
        return parseUserId(authContext.userId()) != null;
    }

    public boolean canCreatePayment(AuthContext authContext, PaymentCreateRequest request) {
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

    public boolean canAccessPayment(AuthContext authContext, Long paymentId) {
        if (authContext == null || paymentId == null) {
            return false;
        }
        if (authContext.role() == UserRole.ADMIN) {
            return true;
        }
        Long userId = parseUserId(authContext.userId());
        if (userId == null) {
            return false;
        }
        return paymentRepository.existsByIdAndUserId(paymentId, userId);
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
