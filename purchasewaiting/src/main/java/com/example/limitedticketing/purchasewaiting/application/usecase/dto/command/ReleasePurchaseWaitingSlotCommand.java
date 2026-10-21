package com.example.limitedticketing.purchasewaiting.application.usecase.dto.command;

import java.util.UUID;

public record ReleasePurchaseWaitingSlotCommand(Long saleProductId, UUID waitingEntryId, UUID processingToken) {
}
