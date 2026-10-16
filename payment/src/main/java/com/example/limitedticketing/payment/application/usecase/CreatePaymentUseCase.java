package com.example.limitedticketing.payment.application.usecase;

import com.example.limitedticketing.payment.application.usecase.dto.command.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.result.PaymentAttemptResult;

public interface CreatePaymentUseCase {

    PaymentAttemptResult createPayment(CreatePaymentCommand command);
}
