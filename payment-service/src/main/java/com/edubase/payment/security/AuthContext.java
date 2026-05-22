package com.edubase.payment.security;

public record AuthContext(String userId, UserRole role) {
}
