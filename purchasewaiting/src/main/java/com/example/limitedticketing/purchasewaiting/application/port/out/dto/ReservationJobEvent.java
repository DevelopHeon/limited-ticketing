package com.example.limitedticketing.purchasewaiting.application.port.out.dto;

import java.time.Instant;
import java.util.UUID;

public record ReservationJobEvent(UUID eventId, String eventType, int schemaVersion, Instant occurredAt,
    UUID waitingEntryId, Long saleProductId, UUID processingToken, String failureCode) {

    public static final String REQUESTED = "PurchaseReservationRequested";
    public static final String FAILED = "PurchaseReservationExecutionFailed";
}
