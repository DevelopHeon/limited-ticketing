package com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto;

import java.util.UUID;

public record ReservationJobInput(UUID eventId, UUID waitingEntryId, Long saleProductId, UUID processingToken) {
}
