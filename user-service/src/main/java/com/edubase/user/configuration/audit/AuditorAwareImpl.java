package com.edubase.user.configuration.audit;

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
            Long userId = extractLongClaim(jwtAuthenticationToken.getToken(), "userId");
            if (userId != null) {
                return Optional.of(userId);
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Long userId = extractLongClaim(jwt, "userId");
            if (userId != null) {
                return Optional.of(userId);
            }
        }

        return Optional.empty();
    }

    private Long extractLongClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
