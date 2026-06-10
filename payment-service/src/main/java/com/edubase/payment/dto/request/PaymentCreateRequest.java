package com.edubase.payment.dto.request;

import com.edubase.payment.entity.PaymentMethod;
import com.edubase.payment.entity.PaymentProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PaymentCreateRequest {

    @NotBlank(message = "courseId is required")
    private String courseId;

    @Positive(message = "userId must be positive")
    private Long userId;

    @NotNull(message = "provider is required")
    private PaymentProvider provider;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @Size(max = 64, message = "idempotencyKey can be at most 64 chars")
    private String idempotencyKey;

    private Boolean autoConfirm;

    @Size(max = 120, message = "buyerFullName can be at most 120 chars")
    private String buyerFullName;

    @Size(max = 160, message = "buyerEmail can be at most 160 chars")
    private String buyerEmail;

    @Size(max = 30, message = "buyerTaxNumber can be at most 30 chars")
    private String buyerTaxNumber;

    @Size(max = 500, message = "buyerAddress can be at most 500 chars")
    private String buyerAddress;
}
