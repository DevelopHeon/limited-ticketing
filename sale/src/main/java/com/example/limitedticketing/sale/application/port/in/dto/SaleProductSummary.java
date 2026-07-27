package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record SaleProductSummary(
    Long saleProductId,
    String productName,
    Money salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    String status
) {

    public static SaleProductSummary of(
        Long saleProductId,
        String productName,
        Money salePrice,
        LocalDateTime saleStartsAt,
        LocalDateTime saleEndsAt,
        String status
    ) {
        return new SaleProductSummary(
            saleProductId,
            productName,
            salePrice,
            saleStartsAt,
            saleEndsAt,
            status
        );
    }
}
