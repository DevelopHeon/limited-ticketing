package com.example.limitedticketing.sale.domain.model;

import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.time.LocalDateTime;

public class SaleProduct {

    private final Long id;
    private final Long productId;
    private final Money price;
    private final SalePeriod salePeriod;
    private final int purchaseLimitPerMember;
    private SaleStatus status;

    private SaleProduct(
            Long id,
            Long productId,
            Money price,
            SalePeriod salePeriod,
            int purchaseLimitPerMember,
            SaleStatus status
    ) {
        this.id = id;
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required");
        }
        if (purchaseLimitPerMember != 1) {
            throw new IllegalArgumentException("Purchase limit per member must be 1");
        }
        this.productId = productId;
        this.price = price;
        this.salePeriod = salePeriod;
        this.purchaseLimitPerMember = purchaseLimitPerMember;
        this.status = status;
    }

    public static SaleProduct schedule(Long productId, Money price, SalePeriod salePeriod) {
        return new SaleProduct(null, productId, price, salePeriod, 1, SaleStatus.SCHEDULED);
    }

    public static SaleProduct reconstitute(
            Long id,
            Long productId,
            Money price,
            SalePeriod salePeriod,
            int purchaseLimitPerMember,
            SaleStatus status
    ) {
        return new SaleProduct(id, productId, price, salePeriod, purchaseLimitPerMember, status);
    }

    public void open(LocalDateTime openedAt) {
        if (!salePeriod.includes(openedAt)) {
            throw new IllegalStateException("Sale can only open during the configured sale period");
        }
        this.status = SaleStatus.OPEN;
    }

    public void close() {
        this.status = SaleStatus.CLOSED;
    }

    public void ensurePurchasableAt(LocalDateTime requestedAt) {
        if (status != SaleStatus.OPEN || !salePeriod.includes(requestedAt)) {
            throw new IllegalStateException("Sale product is not available");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Money getPrice() {
        return price;
    }

    public int getPurchaseLimitPerMember() {
        return purchaseLimitPerMember;
    }

    public SalePeriod getSalePeriod() {
        return salePeriod;
    }

    public SaleStatus getStatus() {
        return status;
    }
}
