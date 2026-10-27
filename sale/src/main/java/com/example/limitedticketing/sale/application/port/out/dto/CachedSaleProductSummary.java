package com.example.limitedticketing.sale.application.port.out.dto;

import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record CachedSaleProductSummary(Long saleProductId, String productName, Money salePrice,
    LocalDateTime saleStartsAt, LocalDateTime saleEndsAt, SaleStatus status) {
}
