package com.example.limitedticketing.purchase.domain.model;

import com.example.limitedticketing.purchase.domain.event.PurchaseReserved;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public class Purchase {

    private final Long id;
    private final Long memberId;
    private final Long saleProductId;
    private final Money price;
    private final int quantity;
    private final LocalDateTime expiresAt;
    private PurchaseStatus status;

    private Purchase(
            Long id,
            Long memberId,
            Long saleProductId,
            Money price,
            int quantity,
            LocalDateTime expiresAt,
            PurchaseStatus status
    ) {
        this.id = id;
        if (memberId == null || saleProductId == null || price == null || expiresAt == null) {
            throw new IllegalArgumentException("Purchase fields are required");
        }
        if (quantity != 1) {
            throw new IllegalArgumentException("Purchase quantity must be 1");
        }
        this.memberId = memberId;
        this.saleProductId = saleProductId;
        this.price = price;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public static Purchase reserve(Long memberId, Long saleProductId, Money price, LocalDateTime expiresAt) {
        return new Purchase(null, memberId, saleProductId, price, 1, expiresAt, PurchaseStatus.PENDING_PAYMENT);
    }

    public static Purchase reconstitute(
            Long id,
            Long memberId,
            Long saleProductId,
            Money price,
            int quantity,
            LocalDateTime expiresAt,
            PurchaseStatus status
    ) {
        return new Purchase(id, memberId, saleProductId, price, quantity, expiresAt, status);
    }

    public void markPaid() {
        if (status != PurchaseStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending purchases can be paid");
        }
        this.status = PurchaseStatus.PAID;
    }

    public void expire(LocalDateTime now) {
        if (status != PurchaseStatus.PENDING_PAYMENT || now.isBefore(expiresAt)) {
            throw new IllegalStateException("Purchase cannot be expired");
        }
        this.status = PurchaseStatus.EXPIRED;
    }

    public void cancel() {
        if (status != PurchaseStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending purchases can be cancelled");
        }
        this.status = PurchaseStatus.CANCELLED;
    }

    public PurchaseReserved reservationEvent() {
        if (id == null || status != PurchaseStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only persisted pending purchases can create reservation events");
        }

        return new PurchaseReserved(id, memberId, saleProductId, price, expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getSaleProductId() {
        return saleProductId;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Money getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }
}
