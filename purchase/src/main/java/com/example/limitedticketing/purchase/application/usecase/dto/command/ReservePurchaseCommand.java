package com.example.limitedticketing.purchase.application.usecase.dto.command;

public record ReservePurchaseCommand(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
