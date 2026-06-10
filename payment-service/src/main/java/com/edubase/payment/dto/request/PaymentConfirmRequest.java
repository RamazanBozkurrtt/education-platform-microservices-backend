package com.edubase.payment.dto.request;

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
public class PaymentConfirmRequest {

    /**
     * true  -> payment succeeds
     * false -> payment fails (simulated gateway decline)
     */
    private Boolean approved;

    @Size(max = 255, message = "failureReason can be at most 255 chars")
    private String failureReason;

    @Size(max = 128, message = "gatewayTransactionId can be at most 128 chars")
    private String gatewayTransactionId;

    private Long gatewayTimestampEpochSeconds;

    @Size(max = 256, message = "gatewaySignature can be at most 256 chars")
    private String gatewaySignature;

    @Size(max = 120, message = "buyerFullName can be at most 120 chars")
    private String buyerFullName;

    @Size(max = 160, message = "buyerEmail can be at most 160 chars")
    private String buyerEmail;

    @Size(max = 30, message = "buyerTaxNumber can be at most 30 chars")
    private String buyerTaxNumber;

    @Size(max = 500, message = "buyerAddress can be at most 500 chars")
    private String buyerAddress;
}
