package com.example.limitedticketing.payment.application.usecase;

import com.example.limitedticketing.payment.application.usecase.dto.command.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.result.PaymentAttemptResult;

public interface ApprovePaymentUseCase {

    PaymentAttemptResult approve(ApprovePaymentCommand command);
}
