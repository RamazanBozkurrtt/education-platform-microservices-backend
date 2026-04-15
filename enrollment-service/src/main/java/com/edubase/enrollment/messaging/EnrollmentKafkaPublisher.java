package com.edubase.enrollment.messaging;

import com.edubase.commonCore.events.EnrollmentCancelledEvent;
import com.edubase.commonCore.events.EnrollmentCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentKafkaPublisher {

    @Value("${app.kafka.topics.enrollment-created:enrollment.created.v1}")
    private String enrollmentCreatedTopic;

    @Value("${app.kafka.topics.enrollment-cancelled:enrollment.cancelled.v1}")
    private String enrollmentCancelledTopic;

    private final KafkaTemplate<String, EnrollmentCreatedEvent> enrollmentCreatedKafkaTemplate;
    private final KafkaTemplate<String, EnrollmentCancelledEvent> enrollmentCancelledKafkaTemplate;

    public void publishCreated(Long enrollmentId, Long userId, String courseId) {
        EnrollmentCreatedEvent event = new EnrollmentCreatedEvent(enrollmentId, userId, courseId, Instant.now());
        enrollmentCreatedKafkaTemplate.send(enrollmentCreatedTopic, String.valueOf(enrollmentId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish enrollment created event for enrollmentId={}", enrollmentId, ex);
                        return;
                    }
                    log.info("Published enrollment created event for enrollmentId={} to topic={}", enrollmentId, enrollmentCreatedTopic);
                });
    }

    public void publishCancelled(Long enrollmentId, Long userId, String courseId) {
        EnrollmentCancelledEvent event = new EnrollmentCancelledEvent(enrollmentId, userId, courseId, Instant.now());
        enrollmentCancelledKafkaTemplate.send(enrollmentCancelledTopic, String.valueOf(enrollmentId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish enrollment cancelled event for enrollmentId={}", enrollmentId, ex);
                        return;
                    }
                    log.info("Published enrollment cancelled event for enrollmentId={} to topic={}", enrollmentId, enrollmentCancelledTopic);
                });
    }
}
