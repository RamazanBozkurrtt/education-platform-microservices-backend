package com.edubase.payment.dto.request;

import com.edubase.payment.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
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
public class PaymentStatusUpdateRequest {

    @NotNull(message = "status is required")
    private PaymentStatus status;

    @Size(max = 255, message = "failureReason can be at most 255 chars")
    private String failureReason;
}
