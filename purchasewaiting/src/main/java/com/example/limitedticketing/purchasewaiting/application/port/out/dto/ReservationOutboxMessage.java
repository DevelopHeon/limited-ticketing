package com.example.limitedticketing.purchasewaiting.application.port.out.dto;

import java.util.UUID;

public record ReservationOutboxMessage(UUID eventId, String topic, String key, String payload, int attempts) {
}
