package com.example.limitedticketing.sale.application.usecase.dto.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSaleProductCommand(
    String productName,
    String benefitDescription,
    BigDecimal salePrice,
    LocalDateTime saleStartsAt,
    LocalDateTime saleEndsAt,
    long initialInventoryQuantity
) {
}
