package com.edubase.review.security;

import com.edubase.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewSecurity {

    private final ReviewRepository reviewRepository;

    public boolean isAuthenticatedUser(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            return false;
        }
        return parseUserId(authContext.userId()) != null;
    }

    public boolean canUpdateReview(AuthContext authContext, Long reviewId) {
        Long userId = parseUserId(authContext == null ? null : authContext.userId());
        if (userId == null || reviewId == null) {
            return false;
        }
        return reviewRepository.existsByIdAndUserId(reviewId, userId);
    }

    public boolean canDeleteReview(AuthContext authContext, Long reviewId) {
        if (authContext == null || reviewId == null) {
            return false;
        }
        if (authContext.role() == UserRole.ADMIN) {
            return true;
        }
        Long userId = parseUserId(authContext.userId());
        if (userId == null) {
            return false;
        }
        return reviewRepository.existsByIdAndUserId(reviewId, userId);
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
