package com.edubase.enrollment.security;

import java.util.Locale;

public enum UserRole {
    ADMIN,
    INSTRUCTOR,
    STUDENT,
    UNKNOWN;

    public static UserRole from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }

        for (UserRole role : values()) {
            if (role.name().equals(normalized)) {
                return role;
            }
        }
        return UNKNOWN;
    }
}
