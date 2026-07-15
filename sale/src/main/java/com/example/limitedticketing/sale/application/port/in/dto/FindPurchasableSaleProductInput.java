package com.example.limitedticketing.sale.application.port.in.dto;

import java.time.LocalDateTime;

public record FindPurchasableSaleProductInput(Long saleProductId, LocalDateTime requestedAt) {
}
