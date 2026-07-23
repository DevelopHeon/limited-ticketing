package com.example.limitedticketing.sale.domain.model;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.time.LocalDateTime;

public class SaleProduct {

    private final Long id;
    private final Long productId;
    private final Money price;
    private SalePeriod salePeriod;
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
            throw SaleException.invalid("Product ID is required");
        }
        if (purchaseLimitPerMember != 1) {
            throw SaleException.invalid("Purchase limit per member must be 1");
        }
        this.productId = productId;
        this.price = price;
        this.salePeriod = salePeriod;
        this.purchaseLimitPerMember = purchaseLimitPerMember;
        this.status = status;
    }

    public static SaleProduct createDraft(Long productId, Money price, SalePeriod salePeriod) {
        return new SaleProduct(null, productId, price, salePeriod, 1, SaleStatus.DRAFT);
    }

    public static SaleProduct schedule(Long productId, Money price, SalePeriod salePeriod) {
        return createDraft(productId, price, salePeriod);
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
        if (status != SaleStatus.DRAFT) {
            throw SaleException.invalidState("Only draft sale products can be opened");
        }
        if (!salePeriod.includes(openedAt)) {
            throw SaleException.outsideSalePeriod();
        }
        this.status = SaleStatus.OPEN;
    }

    public void close() {
        if (status != SaleStatus.OPEN) {
            throw SaleException.invalidState("Only open sale products can be closed");
        }
        this.status = SaleStatus.CLOSED;
    }

    public void changeSalePeriod(SalePeriod salePeriod) {
        if (status != SaleStatus.DRAFT) {
            throw SaleException.invalidState("Only draft sale products can change the sale period");
        }
        this.salePeriod = salePeriod;
    }

    public void ensurePurchasableAt(LocalDateTime requestedAt) {
        if (status != SaleStatus.OPEN || !salePeriod.includes(requestedAt)) {
            throw SaleException.unavailable();
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
