package com.edubase.payment.configuration.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Long userId = extractUserId(jwtAuthenticationToken.getToken());
            if (userId != null) {
                return Optional.of(userId);
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Long userId = extractUserId(jwt);
            if (userId != null) {
                return Optional.of(userId);
            }
        }

        return Optional.empty();
    }

    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("user_id");
        if (userIdClaim instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (userIdClaim instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }

        String tokenId = jwt.getId();
        if (tokenId == null || tokenId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(tokenId.trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }
}
