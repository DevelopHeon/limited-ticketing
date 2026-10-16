package com.example.limitedticketing.purchasewaiting.application.usecase.dto.command;

public record EnqueuePurchaseWaitingCommand(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
