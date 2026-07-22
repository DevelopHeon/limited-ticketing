package com.example.limitedticketing.purchase.domain.model;

import com.example.limitedticketing.purchase.domain.event.PurchaseReserved;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public class Purchase {

    private final Long id;
    private final Long memberId;
    private final Long saleProductId;
    private final String purchaseRequestKey;
    private final Money price;
    private final int quantity;
    private final LocalDateTime expiresAt;
    private PurchaseStatus status;

    private Purchase(
            Long id,
            Long memberId,
            Long saleProductId,
            String purchaseRequestKey,
            Money price,
            int quantity,
            LocalDateTime expiresAt,
            PurchaseStatus status
    ) {
        this.id = id;
        if (memberId == null || saleProductId == null || purchaseRequestKey == null || purchaseRequestKey.isBlank()
            || price == null || expiresAt == null) {
            throw PurchaseException.invalid("Purchase fields are required");
        }
        if (quantity != 1) {
            throw PurchaseException.invalid("Purchase quantity must be 1");
        }
        this.memberId = memberId;
        this.saleProductId = saleProductId;
        this.purchaseRequestKey = purchaseRequestKey;
        this.price = price;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public static Purchase reserve(
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        Money price,
        LocalDateTime expiresAt
    ) {
        return new Purchase(null, memberId, saleProductId, purchaseRequestKey, price, 1, expiresAt, PurchaseStatus.PENDING_PAYMENT);
    }

    public static Purchase reconstitute(
            Long id,
            Long memberId,
            Long saleProductId,
            String purchaseRequestKey,
            Money price,
            int quantity,
            LocalDateTime expiresAt,
            PurchaseStatus status
    ) {
        return new Purchase(id, memberId, saleProductId, purchaseRequestKey, price, quantity, expiresAt, status);
    }

    public void markPaid() {
        if (status != PurchaseStatus.PENDING_PAYMENT) {
            throw PurchaseException.invalidState("Only pending purchases can be paid");
        }
        this.status = PurchaseStatus.PAID;
    }

    public void expire(LocalDateTime now) {
        if (status != PurchaseStatus.PENDING_PAYMENT || now.isBefore(expiresAt)) {
            throw PurchaseException.invalidState("Purchase cannot be expired");
        }
        this.status = PurchaseStatus.EXPIRED;
    }

    public void cancelBy(Long memberId) {
        if (!this.memberId.equals(memberId)) {
            throw PurchaseException.ownershipViolation();
        }
        if (status != PurchaseStatus.PENDING_PAYMENT) {
            throw PurchaseException.invalidState("Only pending purchases can be cancelled");
        }
        this.status = PurchaseStatus.CANCELLED;
    }

    public PurchaseReserved reservationEvent() {
        if (id == null || status != PurchaseStatus.PENDING_PAYMENT) {
            throw PurchaseException.invalidState("Only persisted pending purchases can create reservation events");
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

    public String getPurchaseRequestKey() {
        return purchaseRequestKey;
    }

    public String activePurchaseKey() {
        return switch (status) {
            case PENDING_PAYMENT, PAID -> memberId + ":" + saleProductId;
            case EXPIRED, CANCELLED -> null;
        };
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
