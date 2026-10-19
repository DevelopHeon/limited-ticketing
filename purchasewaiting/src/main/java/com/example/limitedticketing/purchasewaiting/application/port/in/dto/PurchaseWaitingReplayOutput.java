package com.example.limitedticketing.purchasewaiting.application.port.in.dto;

import java.util.UUID;

public record PurchaseWaitingReplayOutput(UUID queueEntryId, String status, Long purchaseId) {
}
