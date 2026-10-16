package com.example.limitedticketing.purchasewaiting.application.port.out.dto;

import java.util.UUID;

public record PurchaseWaitingClaim(UUID waitingEntryId, UUID token) {
}
