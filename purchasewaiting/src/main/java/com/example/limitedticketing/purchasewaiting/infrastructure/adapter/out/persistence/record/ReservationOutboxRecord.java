package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationOutboxRecord(
    UUID eventId,
    UUID waitingEntryId,
    Long saleProductId,
    UUID processingToken,
    String eventType,
    String topic,
    String messageKey,
    String payload,
    LocalDateTime createdAt,
    int publishAttempts,
    UUID claimId
) {
}
