package com.edubase.review.security;

public record AuthContext(String userId, UserRole role) {
}
