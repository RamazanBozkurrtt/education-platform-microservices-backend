package com.edubase.commonCore.events;

import java.time.Instant;

public record EnrollmentCreatedEvent(
        Long enrollmentId,
        Long userId,
        String courseId,
        Instant occurredAt
) {
}
