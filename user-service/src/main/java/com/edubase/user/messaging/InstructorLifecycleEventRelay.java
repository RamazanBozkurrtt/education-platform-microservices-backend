package com.edubase.user.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InstructorLifecycleEventRelay {

    private final InstructorLifecycleKafkaPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInstructorLifecycleEvent(InstructorLifecycleDomainEvent event) {
        publisher.publish(event);
    }
}
