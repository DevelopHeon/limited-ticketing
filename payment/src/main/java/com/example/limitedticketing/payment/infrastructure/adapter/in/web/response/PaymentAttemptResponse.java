package com.example.limitedticketing.payment.infrastructure.adapter.in.web.response;

import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttemptStatus;
import java.math.BigDecimal;

public record PaymentAttemptResponse(Long paymentId, PaymentAttemptStatus status, BigDecimal amount) {

    public static PaymentAttemptResponse from(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
            attempt.paymentId(),
            attempt.status(),
            attempt.amount().getAmount()
        );
    }
}
