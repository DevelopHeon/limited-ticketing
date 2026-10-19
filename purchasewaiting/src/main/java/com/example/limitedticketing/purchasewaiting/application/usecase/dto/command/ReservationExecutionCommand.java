package com.example.limitedticketing.purchasewaiting.application.usecase.dto.command;

import java.util.UUID;

public record ReservationExecutionCommand(UUID eventId, UUID waitingEntryId, Long saleProductId, UUID processingToken) {
}
