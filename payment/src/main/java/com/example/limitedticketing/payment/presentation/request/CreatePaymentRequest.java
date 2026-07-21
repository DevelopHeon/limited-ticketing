package com.example.limitedticketing.payment.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(@NotBlank String paymentRequestKey) {
}
