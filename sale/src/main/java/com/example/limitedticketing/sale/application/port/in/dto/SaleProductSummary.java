package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.sale.application.query.dto.SaleProductSummaryResult;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record SaleProductSummary(
    Long saleProductId,
    String productName,
    Money salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    SaleStatus status
) {

    public static SaleProductSummary from(SaleProductSummaryResult result) {
        return new SaleProductSummary(
            result.saleProductId(),
            result.productName(),
            result.salePrice(),
            result.saleStartsAt(),
            result.saleEndsAt(),
            result.status()
        );
    }
}
