package com.example.limitedticketing.purchasewaiting.application.port.in.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseWaitingOutput(
    UUID waitingEntryId,
    long queueSequence,
    String status,
    Long position,
    Long purchaseId,
    String failureCode,
    LocalDateTime createdAt,
    LocalDateTime processingStartedAt,
    LocalDateTime completedAt
) {
}
