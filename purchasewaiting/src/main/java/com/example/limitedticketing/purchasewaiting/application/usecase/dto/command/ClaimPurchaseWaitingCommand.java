package com.example.limitedticketing.purchasewaiting.application.usecase.dto.command;

import java.util.UUID;

public record ClaimPurchaseWaitingCommand(UUID waitingEntryId, UUID token) {
}
