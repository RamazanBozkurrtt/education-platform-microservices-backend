package com.edubase.commonCore.events;

import java.time.Instant;

public record EnrollmentCancelledEvent(
        Long enrollmentId,
        Long userId,
        String courseId,
        Instant occurredAt
) {
}
