package com.edubase.payment.messaging;

import com.edubase.commonCore.events.PaymentRefundedEvent;
import com.edubase.commonCore.events.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaPublisher {

    @Value("${app.kafka.topics.payment-succeeded:payment.succeeded.v1}")
    private String paymentSucceededTopic;

    @Value("${app.kafka.topics.payment-refunded:payment.refunded.v1}")
    private String paymentRefundedTopic;

    private final KafkaTemplate<String, PaymentSucceededEvent> paymentSucceededKafkaTemplate;
    private final KafkaTemplate<String, PaymentRefundedEvent> paymentRefundedKafkaTemplate;

    public void publishSucceeded(Long paymentId,
                                 Long userId,
                                 String courseId,
                                 BigDecimal amount,
                                 String currency,
                                 String invoiceNumber) {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                paymentId, userId, courseId, amount, currency, invoiceNumber, Instant.now()
        );
        paymentSucceededKafkaTemplate.send(paymentSucceededTopic, String.valueOf(paymentId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment succeeded event for paymentId={}", paymentId, ex);
                        return;
                    }
                    log.info("Published payment succeeded event for paymentId={} to topic={}", paymentId, paymentSucceededTopic);
                });
    }

    public void publishRefunded(Long paymentId,
                                Long userId,
                                String courseId,
                                BigDecimal amount,
                                String currency) {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                paymentId, userId, courseId, amount, currency, Instant.now()
        );
        paymentRefundedKafkaTemplate.send(paymentRefundedTopic, String.valueOf(paymentId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment refunded event for paymentId={}", paymentId, ex);
                        return;
                    }
                    log.info("Published payment refunded event for paymentId={} to topic={}", paymentId, paymentRefundedTopic);
                });
    }
}
