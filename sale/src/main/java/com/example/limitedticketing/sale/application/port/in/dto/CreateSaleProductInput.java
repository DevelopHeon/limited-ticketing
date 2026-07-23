package com.example.limitedticketing.sale.application.port.in.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSaleProductInput(
    String productName,
    String benefitDescription,
    BigDecimal salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    long initialInventoryQuantity
) {
}
