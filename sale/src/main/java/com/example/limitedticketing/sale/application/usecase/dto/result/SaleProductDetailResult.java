package com.example.limitedticketing.sale.application.usecase.dto.result;

import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductDetail;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record SaleProductDetailResult(
    Long saleProductId,
    String productName,
    String benefitDescription,
    Money salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    SaleStatus status
) {

    public static SaleProductDetailResult from(SaleProduct saleProduct, Product product) {
        return new SaleProductDetailResult(
            saleProduct.getId(),
            product.getName(),
            product.getBenefitDescription(),
            saleProduct.getPrice(),
            saleProduct.getSalePeriod().getStartsAt(),
            saleProduct.getSalePeriod().getEndsAt(),
            saleProduct.getStatus()
        );
    }

    public CachedSaleProductDetail toCacheValue() {
        return new CachedSaleProductDetail(saleProductId, productName, benefitDescription, salePrice, saleStartsAt, saleEndsAt, status);
    }

    public static SaleProductDetailResult fromCache(CachedSaleProductDetail value) {
        return new SaleProductDetailResult(value.saleProductId(), value.productName(), value.benefitDescription(),
            value.salePrice(), value.saleStartsAt(), value.saleEndsAt(), value.status());
    }
}
