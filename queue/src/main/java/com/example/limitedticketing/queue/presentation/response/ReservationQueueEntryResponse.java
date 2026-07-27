package com.example.limitedticketing.queue.presentation.response;

import com.example.limitedticketing.queue.application.port.in.dto.ReservationQueueEntry;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationQueueEntryResponse(
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

    public static ReservationQueueEntryResponse from(ReservationQueueEntry entry) {
        return new ReservationQueueEntryResponse(
            entry.queueEntryId(),
            entry.queueSequence(),
            entry.status(),
            entry.position(),
            entry.purchaseId(),
            entry.failureCode(),
            entry.createdAt(),
            entry.processingStartedAt(),
            entry.completedAt()
        );
    }
}
