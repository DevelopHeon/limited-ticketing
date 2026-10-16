package com.example.limitedticketing.purchasewaiting.application.port.in.dto;

public record EnqueuePurchaseWaitingInput(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
