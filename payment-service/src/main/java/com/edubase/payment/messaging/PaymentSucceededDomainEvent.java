package com.edubase.payment.messaging;

import java.math.BigDecimal;

public record PaymentSucceededDomainEvent(
        Long paymentId,
        Long userId,
        String courseId,
        BigDecimal amount,
        String currency,
        String invoiceNumber
) {
}
