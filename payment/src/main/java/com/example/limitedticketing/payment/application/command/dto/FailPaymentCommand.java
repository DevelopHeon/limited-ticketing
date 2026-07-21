package com.example.limitedticketing.payment.application.command.dto;

public record FailPaymentCommand(Long memberId, Long paymentId) {
}
