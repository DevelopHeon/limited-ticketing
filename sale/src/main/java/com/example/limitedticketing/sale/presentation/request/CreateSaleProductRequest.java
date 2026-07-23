package com.example.limitedticketing.sale.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateSaleProductRequest(
    @NotBlank String productName,
    @NotBlank String benefitDescription,
    @NotNull @DecimalMin(value = "0.01") BigDecimal salePrice,
    @NotNull LocalDateTime saleStartsAt,
    @NotNull LocalDateTime saleEndsAt,
    @Positive long initialInventoryQuantity
) {
}
