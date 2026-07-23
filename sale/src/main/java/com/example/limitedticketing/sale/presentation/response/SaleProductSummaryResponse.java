package com.example.limitedticketing.sale.presentation.response;

import com.example.limitedticketing.sale.application.port.in.dto.SaleProductSummary;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleProductSummaryResponse(
    Long saleProductId,
    String productName,
    BigDecimal salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    SaleStatus status
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
