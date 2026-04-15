package com.edubase.enrollment.security;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthContextResolver {

    public AuthContext requireAuth(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String userId = extractUserId(jwt);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        UserRole role = extractRole(jwt);
        return new AuthContext(userId, role);
    }

    private String extractUserId(Jwt jwt) {
        String tokenId = jwt.getId();
        if (tokenId == null || tokenId.isBlank()) {
            return null;
        }
        return tokenId.trim();
    }

    private UserRole extractRole(Jwt jwt) {
        Object claim = jwt.getClaim("role");
        if (claim instanceof String value) {
            return UserRole.from(value);
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            return UserRole.UNKNOWN;
        }

        boolean isAdmin = roles.stream().anyMatch(role -> UserRole.from(role) == UserRole.ADMIN);
        if (isAdmin) {
            return UserRole.ADMIN;
        }

        boolean isInstructor = roles.stream().anyMatch(role -> UserRole.from(role) == UserRole.INSTRUCTOR);
        if (isInstructor) {
            return UserRole.INSTRUCTOR;
        }

        return UserRole.from(roles.get(0));
    }
}
