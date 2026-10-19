package com.example.limitedticketing.purchase.application.usecase.dto.query;

public record FindPurchaseByRequestQuery(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
