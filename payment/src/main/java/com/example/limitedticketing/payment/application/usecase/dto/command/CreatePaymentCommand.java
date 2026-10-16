package com.example.limitedticketing.payment.application.usecase.dto.command;

public record CreatePaymentCommand(Long memberId, Long purchaseId, String paymentRequestKey) {
}
