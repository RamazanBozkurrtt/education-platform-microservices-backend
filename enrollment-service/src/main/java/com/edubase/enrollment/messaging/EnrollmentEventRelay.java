package com.edubase.enrollment.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EnrollmentEventRelay {

    private final EnrollmentKafkaPublisher enrollmentKafkaPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentCreated(EnrollmentCreatedDomainEvent event) {
        enrollmentKafkaPublisher.publishCreated(event.enrollmentId(), event.userId(), event.courseId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnrollmentCancelled(EnrollmentCancelledDomainEvent event) {
        enrollmentKafkaPublisher.publishCancelled(event.enrollmentId(), event.userId(), event.courseId());
    }
}
