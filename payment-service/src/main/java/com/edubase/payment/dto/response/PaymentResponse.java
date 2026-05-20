package com.edubase.payment.dto.response;

import com.edubase.payment.entity.PaymentMethod;
import com.edubase.payment.entity.PaymentProvider;
import com.edubase.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long userId;
    private String courseId;
    private String courseTitleSnapshot;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentProvider provider;
    private PaymentMethod paymentMethod;
    private String providerPaymentId;
    private String idempotencyKey;
    private String invoiceNumber;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
