package com.edubase.commonCore.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRefundedEvent(
        Long paymentId,
        Long userId,
        String courseId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {
}
