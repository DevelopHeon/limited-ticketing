package com.example.limitedticketing.payment.application.port.in.dto;

public record CreatePaymentInput(Long memberId, Long purchaseId, String paymentRequestKey) {
}
