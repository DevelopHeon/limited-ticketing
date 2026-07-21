package com.example.limitedticketing.payment.application.port.in.dto;

import com.example.limitedticketing.payment.application.command.dto.PaymentAttemptResult;
import com.example.limitedticketing.shared.vo.Money;

public record PaymentAttempt(Long paymentId, PaymentAttemptStatus status, Money amount) {

    public static PaymentAttempt from(PaymentAttemptResult result) {
        return new PaymentAttempt(
            result.paymentId(),
            PaymentAttemptStatus.valueOf(result.status().name()),
            result.amount()
        );
    }
}
