package com.example.limitedticketing.payment.application.usecase.dto.command;

public record FailPaymentCommand(Long memberId, Long paymentId) {
}
