package com.example.limitedticketing.sale.infrastructure.adapter.in.web.response;

import com.example.limitedticketing.sale.application.port.in.dto.SaleProductDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleProductDetailResponse(
    Long saleProductId,
    String productName,
    String benefitDescription,
    BigDecimal salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    String status
) {

    public static SaleProductDetailResponse from(SaleProductDetail detail) {
        return new SaleProductDetailResponse(
            detail.saleProductId(),
            detail.productName(),
            detail.benefitDescription(),
            detail.salePrice().getAmount(),
            detail.saleStartsAt(),
            detail.saleEndsAt(),
            detail.status()
        );
    }
}
