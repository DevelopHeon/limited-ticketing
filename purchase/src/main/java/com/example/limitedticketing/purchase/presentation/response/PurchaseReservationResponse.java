package com.example.limitedticketing.purchase.presentation.response;

import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import java.time.LocalDateTime;

public record PurchaseReservationResponse(Long purchaseId, PurchaseStatus status, LocalDateTime expiresAt) {

    public static PurchaseReservationResponse from(PurchaseReservation reservation) {
        return new PurchaseReservationResponse(
            reservation.purchaseId(),
            reservation.status(),
            reservation.expiresAt()
        );
    }
}
