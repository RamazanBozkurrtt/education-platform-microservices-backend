package com.edubase.payment.entity;

import com.edubase.commonJpa.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoices_payment_id", columnNames = "payment_id"),
                @UniqueConstraint(name = "uk_invoices_invoice_number", columnNames = "invoice_number")
        },
        indexes = {
                @Index(name = "idx_invoices_payment_id", columnList = "payment_id"),
                @Index(name = "idx_invoices_invoice_number", columnList = "invoice_number", unique = true)
        }
)
@AttributeOverride(
        name = "id",
        column = @Column(name = "invoice_id")
)
public class Invoice extends BaseEntity {

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "invoice_number", nullable = false, length = 40, unique = true)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDateTime invoiceDate;

    @Column(name = "buyer_full_name", length = 120)
    private String buyerFullName;

    @Column(name = "buyer_email", length = 160)
    private String buyerEmail;

    @Column(name = "buyer_tax_number", length = 30)
    private String buyerTaxNumber;

    @Column(name = "buyer_address", length = 500)
    private String buyerAddress;

    @Column(name = "course_title_snapshot", nullable = false, length = 255)
    private String courseTitleSnapshot;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
}
