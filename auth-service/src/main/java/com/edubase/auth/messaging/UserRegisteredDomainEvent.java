package com.edubase.auth.messaging;

public record UserRegisteredDomainEvent(
        Long userId,
        String email
) {
}
