package com.example.limitedticketing.queue.application.port.in.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationQueueEntry(
    UUID queueEntryId,
    long queueSequence,
    String status,
    Long position,
    Long purchaseId,
    String failureCode,
    LocalDateTime createdAt,
    LocalDateTime processingStartedAt,
    LocalDateTime completedAt
) {

    public static ReservationQueueEntry of(
        UUID queueEntryId,
        long queueSequence,
        String status,
        Long position,
        Long purchaseId,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime processingStartedAt,
        LocalDateTime completedAt
    ) {
        return new ReservationQueueEntry(
            queueEntryId,
            queueSequence,
            status,
            position,
            purchaseId,
            failureCode,
            createdAt,
            processingStartedAt,
            completedAt
        );
    }
}
