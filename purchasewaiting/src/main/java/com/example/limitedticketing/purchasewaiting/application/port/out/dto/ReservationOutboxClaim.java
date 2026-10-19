package com.example.limitedticketing.purchasewaiting.application.port.out.dto;

import java.util.UUID;

public record ReservationOutboxClaim(ReservationOutboxMessage message, UUID claimId) {
}
