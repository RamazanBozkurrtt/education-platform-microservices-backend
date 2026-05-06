package com.edubase.commonCore.events;

import java.time.Instant;

public record CourseRatingUpdatedEvent(
        String eventId,
        Long eventVersion,
        Instant occurredAt,
        String courseId,
        Double averageRating,
        Long ratingCount
) {
}
