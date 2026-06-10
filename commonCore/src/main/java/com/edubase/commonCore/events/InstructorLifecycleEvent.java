package com.edubase.commonCore.events;

import java.time.Instant;

public record InstructorLifecycleEvent(
        String eventId,
        InstructorEventType eventType,
        Long eventVersion,
        Instant occurredAt,
        String instructorId,
        String fullName,
        String email,
        String profileImageUrl,
        String headline,
        InstructorStatus status
) {
}
