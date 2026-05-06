package com.edubase.user.messaging;

import com.edubase.commonCore.events.UserRegisteredEvent;
import com.edubase.commonCore.events.InstructorEventType;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.entity.UserStatus;
import com.edubase.user.repository.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

    private final UserProfileRepository userProfileRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.user-registered:user.registered.v1}")
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event == null || event.userId() == null || event.email() == null || event.email().isBlank()) {
            log.warn("Ignoring malformed user registered event: {}", event);
            return;
        }

        String normalizedEmail = event.email().trim().toLowerCase();

        userProfileRepository.findByAuthUserId(event.userId()).ifPresentOrElse(existing -> {
                    boolean changed = false;
                    if (!normalizedEmail.equals(existing.getEmail())) {
                        existing.setEmail(normalizedEmail);
                        changed = true;
                    } else if (existing.getStatus() == null) {
                        existing.setStatus(UserStatus.ACTUAL);
                        changed = true;
                    }
                    if (changed) {
                        userProfileRepository.save(existing);
                        applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                                InstructorEventType.INSTRUCTOR_UPDATED,
                                existing
                        ));
                    }
                }, () -> upsertByEmailOrCreate(event.userId(), normalizedEmail));

        log.info("Processed user registered event for authUserId={}", event.userId());
    }

    private void upsertByEmailOrCreate(Long authUserId, String normalizedEmail) {
        userProfileRepository.findByEmailIgnoreCase(normalizedEmail).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (existing.getAuthUserId() == null) {
                existing.setAuthUserId(authUserId);
                changed = true;
            }
            if (existing.getStatus() == null) {
                existing.setStatus(UserStatus.ACTUAL);
                changed = true;
            }
            if (changed) {
                userProfileRepository.save(existing);
                applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                        InstructorEventType.INSTRUCTOR_UPDATED,
                        existing
                ));
            }
        }, () -> {
            UserProfile profile = UserProfile.builder()
                    .email(normalizedEmail)
                    .authUserId(authUserId)
                    .status(UserStatus.ACTUAL)
                    .build();
            userProfileRepository.save(profile);
            applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(
                    InstructorEventType.INSTRUCTOR_CREATED,
                    profile
            ));
        });
    }
}
