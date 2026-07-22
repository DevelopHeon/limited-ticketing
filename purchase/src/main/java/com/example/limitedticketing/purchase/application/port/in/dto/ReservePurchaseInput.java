package com.example.limitedticketing.purchase.application.port.in.dto;

public record ReservePurchaseInput(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
