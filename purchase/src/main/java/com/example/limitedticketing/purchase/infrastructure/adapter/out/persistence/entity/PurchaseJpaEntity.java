package com.example.limitedticketing.purchase.infrastructure.adapter.out.persistence.entity;

import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
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
        name = "purchases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_active_purchase_key",
                columnNames = "active_purchase_key"
        )
)
public class PurchaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "sale_product_id", nullable = false, updatable = false)
    private Long saleProductId;

    @Column(name = "purchase_request_key", nullable = false, updatable = false, length = 100)
    private String purchaseRequestKey;

    @Column(name = "active_purchase_key", length = 100)
    private String activePurchaseKey;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal purchasePrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseStatus status;

    protected PurchaseJpaEntity() {
    }

    private PurchaseJpaEntity(Purchase purchase) {
        this.id = purchase.getId();
        this.memberId = purchase.getMemberId();
        this.saleProductId = purchase.getSaleProductId();
        this.purchaseRequestKey = purchase.getPurchaseRequestKey();
        this.activePurchaseKey = activePurchaseKey(purchase);
        this.purchasePrice = purchase.getPrice().getAmount();
        this.quantity = purchase.getQuantity();
        this.expiresAt = purchase.getExpiresAt();
        this.status = purchase.getStatus();
    }

    public static PurchaseJpaEntity from(Purchase purchase) {
        return new PurchaseJpaEntity(purchase);
    }

    public Purchase toDomain() {
        return Purchase.reconstitute(
                id,
                memberId,
                saleProductId,
                purchaseRequestKey,
                Money.of(purchasePrice),
                quantity,
                expiresAt,
                status
        );
    }

    private static String activePurchaseKey(Purchase purchase) {
        return purchase.activePurchaseKey();
    }
}
