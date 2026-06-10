package com.edubase.payment.dto.response;

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
public class InvoiceResponse {

    private Long id;
    private Long paymentId;
    private String invoiceNumber;
    private LocalDateTime invoiceDate;
    private String buyerFullName;
    private String buyerEmail;
    private String buyerTaxNumber;
    private String buyerAddress;
    private String courseTitleSnapshot;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime createdAt;
}
