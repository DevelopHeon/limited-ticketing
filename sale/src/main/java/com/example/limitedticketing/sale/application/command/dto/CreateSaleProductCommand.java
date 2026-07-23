package com.example.limitedticketing.sale.application.command.dto;

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
