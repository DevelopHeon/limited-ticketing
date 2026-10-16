package com.example.limitedticketing.payment.infrastructure.adapter.out.persistence.entity;

import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.model.PaymentStatus;
import com.example.limitedticketing.shared.vo.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_request_key", columnNames = "payment_request_key")
)
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private Long purchaseId;

    @Column(name = "payment_request_key", nullable = false, updatable = false, length = 100)
    private String paymentRequestKey;

    @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private LocalDateTime approvedAt;

    protected PaymentJpaEntity() {
    }

    private PaymentJpaEntity(Payment payment) {
        this.id = payment.getId();
        this.purchaseId = payment.getPurchaseId();
        this.paymentRequestKey = payment.getPaymentRequestKey();
        this.paymentAmount = payment.getAmount().getAmount();
        this.status = payment.getStatus();
        this.approvedAt = payment.getApprovedAt();
    }

    public static PaymentJpaEntity from(Payment payment) {
        return new PaymentJpaEntity(payment);
    }

    public Payment toDomain() {
        return Payment.reconstitute(
                id,
                purchaseId,
                paymentRequestKey,
                Money.of(paymentAmount),
                status,
                approvedAt
        );
    }
}
