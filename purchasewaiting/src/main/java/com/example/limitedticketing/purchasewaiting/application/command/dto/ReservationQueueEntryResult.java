package com.example.limitedticketing.purchasewaiting.application.command.dto;

import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationQueueEntryResult(
    UUID queueEntryId,
    Long memberId,
    Long saleProductId,
    long queueSequence,
    ReservationQueueStatus status,
    Long purchaseId,
    String failureCode,
    LocalDateTime createdAt,
    LocalDateTime processingStartedAt,
    LocalDateTime completedAt
) {

    public static ReservationQueueEntryResult from(ReservationQueueEntry entry) {
        return new ReservationQueueEntryResult(
            entry.getId(),
            entry.getMemberId(),
            entry.getSaleProductId(),
            entry.getQueueSequence(),
            entry.getStatus(),
            entry.getPurchaseId(),
            entry.getFailureCode(),
            entry.getCreatedAt(),
            entry.getProcessingStartedAt(),
            entry.getCompletedAt()
        );
    }
}
