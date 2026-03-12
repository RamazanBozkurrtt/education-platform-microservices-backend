package com.edubase.course.security;

public record AuthContext(String userId, UserRole role) {
}
