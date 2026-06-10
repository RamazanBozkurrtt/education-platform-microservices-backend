package com.edubase.user.messaging;

import com.edubase.commonCore.events.InstructorLifecycleEvent;
import com.edubase.commonCore.events.InstructorStatus;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.entity.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstructorLifecycleKafkaPublisher {

    @Value("${app.kafka.topics.instructor-lifecycle:instructor.lifecycle.v1}")
    private String instructorLifecycleTopic;

    private final KafkaTemplate<String, InstructorLifecycleEvent> kafkaTemplate;

    public void publish(InstructorLifecycleDomainEvent domainEvent) {
        UserProfile profile = domainEvent.profile();
        if (profile == null || profile.getAuthUserId() == null) {
            log.warn("Instructor event skipped because profile/authUserId missing. type={}", domainEvent.eventType());
            return;
        }

        String instructorId = String.valueOf(profile.getAuthUserId());
        Instant occurredAt = Instant.now();
        Long eventVersion = profile.getUpdatedAt() == null
                ? occurredAt.toEpochMilli()
                : profile.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();

        InstructorLifecycleEvent event = new InstructorLifecycleEvent(
                UUID.randomUUID().toString(),
                domainEvent.eventType(),
                eventVersion,
                occurredAt,
                instructorId,
                fullNameOf(profile),
                profile.getEmail(),
                profile.getAvatarUrl(),
                profile.getHeadline(),
                toInstructorStatus(profile.getStatus())
        );

        kafkaTemplate.send(instructorLifecycleTopic, instructorId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish instructor lifecycle event for instructorId={} type={}",
                                instructorId, domainEvent.eventType(), ex);
                        return;
                    }
                    log.info("Published instructor lifecycle event for instructorId={} type={} topic={}",
                            instructorId, domainEvent.eventType(), instructorLifecycleTopic);
                });
    }

    private String fullNameOf(UserProfile profile) {
        String firstName = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile.getLastName() == null ? "" : profile.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        return profile.getEmail();
    }

    private InstructorStatus toInstructorStatus(UserStatus status) {
        if (status == UserStatus.DEACTIVATED || status == UserStatus.BANNED) {
            return InstructorStatus.DEACTIVATED;
        }
        return InstructorStatus.ACTIVE;
    }
}
