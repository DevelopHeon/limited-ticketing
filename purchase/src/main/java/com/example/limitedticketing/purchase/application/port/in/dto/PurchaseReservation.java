package com.example.limitedticketing.purchase.application.port.in.dto;

import java.time.LocalDateTime;

public record PurchaseReservation(Long purchaseId, String status, LocalDateTime expiresAt) {

    public static PurchaseReservation of(Long purchaseId, String status, LocalDateTime expiresAt) {
        return new PurchaseReservation(purchaseId, status, expiresAt);
    }
}
