package com.example.limitedticketing.payment.domain.model;

import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public class Payment {

    private final Long id;
    private final Long purchaseId;
    private final String paymentRequestKey;
    private final Money amount;
    private PaymentStatus status;
    private LocalDateTime approvedAt;

    private Payment(
            Long id,
            Long purchaseId,
            String paymentRequestKey,
            Money amount,
            PaymentStatus status,
            LocalDateTime approvedAt
    ) {
        this.id = id;
        if (purchaseId == null || paymentRequestKey == null || paymentRequestKey.isBlank() || amount == null) {
            throw PaymentException.invalid("Payment fields are required");
        }
        this.purchaseId = purchaseId;
        this.paymentRequestKey = paymentRequestKey;
        this.amount = amount;
        this.status = status;
        this.approvedAt = approvedAt;
    }

    public static Payment request(Long purchaseId, String paymentRequestKey, Money amount) {
        return new Payment(null, purchaseId, paymentRequestKey, amount, PaymentStatus.READY, null);
    }

    public static Payment reconstitute(
            Long id,
            Long purchaseId,
            String paymentRequestKey,
            Money amount,
            PaymentStatus status,
            LocalDateTime approvedAt
    ) {
        return new Payment(id, purchaseId, paymentRequestKey, amount, status, approvedAt);
    }

    public void approve(LocalDateTime approvedAt) {
        if (status != PaymentStatus.READY || approvedAt == null) {
            throw PaymentException.invalidState("Only ready payments can be approved");
        }
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    public void fail() {
        if (status != PaymentStatus.READY) {
            throw PaymentException.invalidState("Only ready payments can fail");
        }
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        if (status != PaymentStatus.READY) {
            throw PaymentException.invalidState("Only ready payments can be cancelled");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentRequestKey() {
        return paymentRequestKey;
    }

    public Money getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
}
