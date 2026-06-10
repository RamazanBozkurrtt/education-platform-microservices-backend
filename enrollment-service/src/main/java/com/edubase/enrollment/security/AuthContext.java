package com.edubase.enrollment.security;

public record AuthContext(String userId, UserRole role) {
}
