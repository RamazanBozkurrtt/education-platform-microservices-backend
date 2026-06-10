package com.edubase.enrollment.messaging;

public record EnrollmentCancelledDomainEvent(
        Long enrollmentId,
        Long userId,
        String courseId
) {
}
