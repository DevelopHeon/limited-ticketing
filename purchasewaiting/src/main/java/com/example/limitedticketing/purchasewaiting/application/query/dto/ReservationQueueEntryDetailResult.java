package com.example.limitedticketing.purchasewaiting.application.query.dto;

import com.example.limitedticketing.purchasewaiting.application.command.dto.ReservationQueueEntryResult;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationQueueEntryDetailResult(
    UUID queueEntryId,
    long queueSequence,
    ReservationQueueStatus status,
    Long position,
    Long purchaseId,
    String failureCode,
    LocalDateTime createdAt,
    LocalDateTime processingStartedAt,
    LocalDateTime completedAt
) {

    public static ReservationQueueEntryDetailResult from(ReservationQueueEntryResult entry, Long position) {
        return new ReservationQueueEntryDetailResult(
            entry.queueEntryId(),
            entry.queueSequence(),
            entry.status(),
            position,
            entry.purchaseId(),
            entry.failureCode(),
            entry.createdAt(),
            entry.processingStartedAt(),
            entry.completedAt()
        );
    }
}
