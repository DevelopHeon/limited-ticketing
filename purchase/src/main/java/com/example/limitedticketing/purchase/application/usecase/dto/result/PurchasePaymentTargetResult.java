package com.example.limitedticketing.purchase.application.usecase.dto.result;

import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record PurchasePaymentTargetResult(
    Long purchaseId,
    Long memberId,
    Long saleProductId,
    Money amount,
    int quantity,
    LocalDateTime expiresAt,
    PurchaseStatus status
) {
}
