package com.example.limitedticketing.purchase.domain.event;

import com.example.limitedticketing.shared.vo.Money;
import java.time.LocalDateTime;

public record PurchaseReserved(
    Long purchaseId,
    Long memberId,
    Long saleProductId,
    Money price,
    LocalDateTime expiresAt
) {

    public PurchaseReserved {
        if (purchaseId == null || memberId == null || saleProductId == null || price == null || expiresAt == null) {
            throw new IllegalArgumentException("Purchase reservation event fields are required");
        }
    }
}
