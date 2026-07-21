package com.example.limitedticketing.purchase.application.command.dto;

public record CancelPurchaseCommand(Long purchaseId, Long memberId) {
}
