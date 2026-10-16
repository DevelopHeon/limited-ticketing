package com.example.limitedticketing.sale.application.usecase.dto.result;

import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record SaleProductSummaryResult(
    Long saleProductId,
    String productName,
    Money salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    SaleStatus status
) {

    public static SaleProductSummaryResult from(SaleProduct saleProduct, Product product) {
        return new SaleProductSummaryResult(
            saleProduct.getId(),
            product.getName(),
            saleProduct.getPrice(),
            saleProduct.getSalePeriod().getStartsAt(),
            saleProduct.getSalePeriod().getEndsAt(),
            saleProduct.getStatus()
        );
    }
}
