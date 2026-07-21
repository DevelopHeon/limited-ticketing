package com.example.limitedticketing.purchase.application.port.in.dto;

import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record PurchasePaymentTarget(
    Long purchaseId,
    Long memberId,
    Long saleProductId,
    Money amount,
    int quantity,
    LocalDateTime expiresAt,
    PurchasePaymentStatus status
) {
}
