package com.example.limitedticketing.sale.application.usecase.dto.query;

import java.time.LocalDateTime;

public record FindPurchasableSaleProductQuery(Long saleProductId, LocalDateTime requestedAt) {
}
