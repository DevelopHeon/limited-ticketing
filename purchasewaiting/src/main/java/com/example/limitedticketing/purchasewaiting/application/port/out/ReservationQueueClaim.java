package com.example.limitedticketing.purchasewaiting.application.port.out;

import java.util.UUID;

public record ReservationQueueClaim(UUID queueEntryId, UUID token) {
}
