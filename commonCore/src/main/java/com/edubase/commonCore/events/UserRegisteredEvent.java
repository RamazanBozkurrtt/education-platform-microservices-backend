package com.edubase.commonCore.events;

import java.time.Instant;

public record UserRegisteredEvent(
        Long userId,
        String email,
        Instant occurredAt
) {
}
