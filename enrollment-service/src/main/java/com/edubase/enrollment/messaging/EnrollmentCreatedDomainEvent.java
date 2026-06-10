package com.edubase.enrollment.messaging;

public record EnrollmentCreatedDomainEvent(
        Long enrollmentId,
        Long userId,
        String courseId
) {
}
