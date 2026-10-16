package com.example.limitedticketing.sale.infrastructure.adapter.in.web.response;

import com.example.limitedticketing.sale.application.port.in.dto.SaleProductSummary;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleProductSummaryResponse(
    Long saleProductId,
    String productName,
    BigDecimal salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    String status
) {

    public static SaleProductSummaryResponse from(SaleProductSummary summary) {
        return new SaleProductSummaryResponse(
            summary.saleProductId(),
            summary.productName(),
            summary.salePrice().getAmount(),
            summary.saleStartsAt(),
            summary.saleEndsAt(),
            summary.status()
        );
    }
}
