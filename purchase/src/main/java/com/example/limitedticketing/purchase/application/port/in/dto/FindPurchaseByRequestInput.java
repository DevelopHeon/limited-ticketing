package com.example.limitedticketing.purchase.application.port.in.dto;

public record FindPurchaseByRequestInput(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
