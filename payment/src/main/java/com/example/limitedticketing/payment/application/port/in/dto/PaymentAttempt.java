package com.example.limitedticketing.payment.application.port.in.dto;

import com.example.limitedticketing.shared.vo.Money;

public record PaymentAttempt(Long paymentId, PaymentAttemptStatus status, Money amount) {

    public static PaymentAttempt of(Long paymentId, PaymentAttemptStatus status, Money amount) {
        return new PaymentAttempt(paymentId, status, amount);
    }
}
