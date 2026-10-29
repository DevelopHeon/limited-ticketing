package com.example.limitedticketing.purchase.application.usecase.dto.result;

import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import java.time.LocalDateTime;

public record PurchaseReservationResult(Long purchaseId, PurchaseStatus status, LocalDateTime expiresAt) {

    public static PurchaseReservationResult of(Purchase purchase, Long saleProductId) {
        if (!purchase.getSaleProductId().equals(saleProductId)) {
            throw PurchaseException.requestKeyReuse();
        }

        return new PurchaseReservationResult(purchase.getId(), purchase.getStatus(), purchase.getExpiresAt());
    }
}
