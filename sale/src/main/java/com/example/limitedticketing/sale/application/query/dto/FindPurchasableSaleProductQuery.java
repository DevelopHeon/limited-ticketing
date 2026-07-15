package com.example.limitedticketing.sale.application.query.dto;

import java.time.LocalDateTime;

public record FindPurchasableSaleProductQuery(Long saleProductId, LocalDateTime requestedAt) {
}
