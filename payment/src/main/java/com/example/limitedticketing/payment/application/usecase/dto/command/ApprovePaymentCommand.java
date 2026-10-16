package com.example.limitedticketing.payment.application.usecase.dto.command;

public record ApprovePaymentCommand(Long memberId, Long paymentId) {
}
