package com.example.limitedticketing.payment.application.command.dto;

public record ApprovePaymentCommand(Long memberId, Long paymentId) {
}
