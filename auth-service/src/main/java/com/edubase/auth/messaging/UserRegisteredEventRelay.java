package com.edubase.auth.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserRegisteredEventRelay {

    private final UserRegisteredKafkaPublisher userRegisteredKafkaPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredDomainEvent event) {
        userRegisteredKafkaPublisher.publish(event.userId(), event.email());
    }
}
