package com.example.limitedticketing.payment.domain.repository;

import com.example.limitedticketing.payment.domain.model.Payment;

public record IdempotentPayment(Payment payment, boolean created) {
}
