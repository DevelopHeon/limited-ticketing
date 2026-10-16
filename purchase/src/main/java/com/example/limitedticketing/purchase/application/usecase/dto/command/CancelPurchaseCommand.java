package com.example.limitedticketing.purchase.application.usecase.dto.command;

public record CancelPurchaseCommand(Long purchaseId, Long memberId) {
}
