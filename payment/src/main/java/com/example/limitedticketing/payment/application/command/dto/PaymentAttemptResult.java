package com.example.limitedticketing.payment.application.command.dto;

import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.model.PaymentStatus;
import com.example.limitedticketing.shared.vo.Money;

public record PaymentAttemptResult(Long paymentId, PaymentStatus status, Money amount) {

    public static PaymentAttemptResult of(Payment payment) {
        return new PaymentAttemptResult(payment.getId(), payment.getStatus(), payment.getAmount());
    }
}
