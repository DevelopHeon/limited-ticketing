package com.example.limitedticketing.purchasewaiting.application.usecase.dto.command;

public record ReserveWaitingPurchaseCommand(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
