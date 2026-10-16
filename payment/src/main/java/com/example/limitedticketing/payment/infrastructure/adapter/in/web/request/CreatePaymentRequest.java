package com.example.limitedticketing.payment.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(@NotBlank String paymentRequestKey) {
}
