package com.edubase.commonCore.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSucceededEvent(
        Long paymentId,
        Long userId,
        String courseId,
        BigDecimal amount,
        String currency,
        String invoiceNumber,
        Instant occurredAt
) {
}
