package com.example.limitedticketing.purchase.presentation.response;

import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import java.time.LocalDateTime;

public record PurchaseReservationResponse(Long purchaseId, PurchaseStatus status, LocalDateTime expiresAt) {
}
