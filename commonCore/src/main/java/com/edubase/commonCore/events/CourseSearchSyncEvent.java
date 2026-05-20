package com.edubase.commonCore.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CourseSearchSyncEvent(
        String eventId,
        CourseSearchSyncEventType eventType,
        Long eventVersion,
        Instant occurredAt,
        String courseId,
        String title,
        String description,
        String instructorId,
        String categoryId,
        List<String> categoryIds,
        BigDecimal price,
        String status,
        List<String> tags,
        List<String> learningOutcomes
) {
}
