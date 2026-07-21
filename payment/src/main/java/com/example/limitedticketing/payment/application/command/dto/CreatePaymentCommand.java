package com.example.limitedticketing.payment.application.command.dto;

public record CreatePaymentCommand(Long memberId, Long purchaseId, String paymentRequestKey) {
}
