package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.sale.application.query.dto.SaleProductDetailResult;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record SaleProductDetail(
    Long saleProductId,
    String productName,
    String benefitDescription,
    Money salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    SaleStatus status
) {

    public static SaleProductDetail from(SaleProductDetailResult result) {
        return new SaleProductDetail(
            result.saleProductId(),
            result.productName(),
            result.benefitDescription(),
            result.salePrice(),
            result.saleStartsAt(),
            result.saleEndsAt(),
            result.status()
        );
    }
}
