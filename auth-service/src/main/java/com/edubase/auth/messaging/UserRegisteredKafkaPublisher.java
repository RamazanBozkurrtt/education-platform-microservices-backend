package com.edubase.auth.messaging;

import com.edubase.commonCore.events.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredKafkaPublisher {

    @Value("${app.kafka.topics.user-registered:user.registered.v1}")
    private String userRegisteredTopic;

    private final KafkaTemplate<String, UserRegisteredEvent> kafkaTemplate;

    public void publish(Long userId, String email) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, Instant.now());
        kafkaTemplate.send(userRegisteredTopic, String.valueOf(userId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user registered event for userId={}", userId, ex);
                        return;
                    }
                    log.info("Published user registered event for userId={} to topic={}", userId, userRegisteredTopic);
                });
    }
}
