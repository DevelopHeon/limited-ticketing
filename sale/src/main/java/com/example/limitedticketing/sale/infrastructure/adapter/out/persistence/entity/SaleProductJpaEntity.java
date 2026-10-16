package com.example.limitedticketing.sale.infrastructure.adapter.out.persistence.entity;

import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sale_products")
public class SaleProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_starts_at", nullable = false)
    private LocalDateTime saleStartsAt;

    @Column(name = "sale_ends_at", nullable = false)
    private LocalDateTime saleEndsAt;

    @Column(nullable = false)
    private int purchaseLimitPerMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    protected SaleProductJpaEntity() {
    }

    private SaleProductJpaEntity(SaleProduct saleProduct) {
        this.id = saleProduct.getId();
        this.productId = saleProduct.getProductId();
        this.salePrice = saleProduct.getPrice().getAmount();
        this.saleStartsAt = saleProduct.getSalePeriod().getStartsAt();
        this.saleEndsAt = saleProduct.getSalePeriod().getEndsAt();
        this.purchaseLimitPerMember = saleProduct.getPurchaseLimitPerMember();
        this.status = saleProduct.getStatus();
    }

    public static SaleProductJpaEntity from(SaleProduct saleProduct) {
        return new SaleProductJpaEntity(saleProduct);
    }

    public SaleProduct toDomain() {
        return SaleProduct.reconstitute(
                id,
                productId,
                Money.of(salePrice),
                SalePeriod.of(saleStartsAt, saleEndsAt),
                purchaseLimitPerMember,
                status
        );
    }
}
