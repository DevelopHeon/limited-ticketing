package com.example.limitedticketing.purchase.application.command.dto;

import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import java.time.LocalDateTime;

public record PurchaseReservationResult(Long purchaseId, PurchaseStatus status, LocalDateTime expiresAt) {
}
