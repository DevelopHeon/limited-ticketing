package com.example.limitedticketing.purchasewaiting.application.usecase.dto.result;

import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseWaitingResult(
    UUID waitingEntryId,
    long queueSequence,
    PurchaseWaitingStatus status,
    Long position,
    Long purchaseId,
    String failureCode,
    LocalDateTime createdAt,
    LocalDateTime processingStartedAt,
    LocalDateTime completedAt
) {

    public static PurchaseWaitingResult from(PurchaseWaitingEntry entry, Long position) {
        return new PurchaseWaitingResult(entry.getId(), entry.getQueueSequence(), entry.getStatus(), position,
            entry.getPurchaseId(), entry.getFailureCode(), entry.getCreatedAt(),
            entry.getProcessingStartedAt(), entry.getCompletedAt());
    }
}
