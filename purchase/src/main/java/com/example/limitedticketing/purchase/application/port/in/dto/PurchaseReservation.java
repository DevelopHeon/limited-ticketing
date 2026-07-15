package com.example.limitedticketing.purchase.application.port.in.dto;

import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import java.time.LocalDateTime;

public record PurchaseReservation(Long purchaseId, PurchaseStatus status, LocalDateTime expiresAt) {
}
