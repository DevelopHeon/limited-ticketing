package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record SaleProductDetail(
    Long saleProductId,
    String productName,
    String benefitDescription,
    Money salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    String status
) {

    public static SaleProductDetail of(
        Long saleProductId,
        String productName,
        String benefitDescription,
        Money salePrice,
        LocalDateTime saleStartsAt,
        LocalDateTime saleEndsAt,
        String status
    ) {
        return new SaleProductDetail(
            saleProductId,
            productName,
            benefitDescription,
            salePrice,
            saleStartsAt,
            saleEndsAt,
            status
        );
    }
}
