package com.example.limitedticketing.payment.application.usecase;

import com.example.limitedticketing.payment.application.usecase.dto.command.FailPaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.result.PaymentAttemptResult;

public interface FailPaymentUseCase {

    PaymentAttemptResult fail(FailPaymentCommand command);
}
