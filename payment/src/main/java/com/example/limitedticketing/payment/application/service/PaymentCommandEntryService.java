package com.example.limitedticketing.payment.application.service;

import com.example.limitedticketing.payment.application.port.in.PaymentCommandPort;
import com.example.limitedticketing.payment.application.port.in.dto.ApprovePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.CreatePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.FailPaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttemptStatus;
import com.example.limitedticketing.payment.application.usecase.ApprovePaymentUseCase;
import com.example.limitedticketing.payment.application.usecase.CreatePaymentUseCase;
import com.example.limitedticketing.payment.application.usecase.FailPaymentUseCase;
import com.example.limitedticketing.payment.application.usecase.dto.command.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.command.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.command.FailPaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.result.PaymentAttemptResult;
import org.springframework.stereotype.Service;

@Service
public class PaymentCommandEntryService implements PaymentCommandPort {

    private final CreatePaymentUseCase createPaymentUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final FailPaymentUseCase failPaymentUseCase;

    public PaymentCommandEntryService(
        CreatePaymentUseCase createPaymentUseCase,
        ApprovePaymentUseCase approvePaymentUseCase,
        FailPaymentUseCase failPaymentUseCase
    ) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.failPaymentUseCase = failPaymentUseCase;
    }

    public PaymentAttempt createPayment(CreatePaymentInput input) {
        PaymentAttemptResult result = createPaymentUseCase.createPayment(
            new CreatePaymentCommand(input.memberId(), input.purchaseId(), input.paymentRequestKey())
        );
        return PaymentAttempt.of(result.paymentId(), PaymentAttemptStatus.valueOf(result.status().name()), result.amount());
    }

    public PaymentAttempt approve(ApprovePaymentInput input) {
        PaymentAttemptResult result = approvePaymentUseCase.approve(
            new ApprovePaymentCommand(input.memberId(), input.paymentId())
        );
        return PaymentAttempt.of(result.paymentId(), PaymentAttemptStatus.valueOf(result.status().name()), result.amount());
    }

    public PaymentAttempt fail(FailPaymentInput input) {
        PaymentAttemptResult result = failPaymentUseCase.fail(
            new FailPaymentCommand(input.memberId(), input.paymentId())
        );
        return PaymentAttempt.of(result.paymentId(), PaymentAttemptStatus.valueOf(result.status().name()), result.amount());
    }
}
