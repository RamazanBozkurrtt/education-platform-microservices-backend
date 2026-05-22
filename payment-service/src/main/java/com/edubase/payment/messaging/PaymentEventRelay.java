package com.edubase.payment.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentEventRelay {

    private final PaymentKafkaPublisher paymentKafkaPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededDomainEvent event) {
        paymentKafkaPublisher.publishSucceeded(
                event.paymentId(),
                event.userId(),
                event.courseId(),
                event.amount(),
                event.currency(),
                event.invoiceNumber()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRefunded(PaymentRefundedDomainEvent event) {
        paymentKafkaPublisher.publishRefunded(
                event.paymentId(),
                event.userId(),
                event.courseId(),
                event.amount(),
                event.currency()
        );
    }
}
