package com.example.limitedticketing.payment.application.port.in;

import com.example.limitedticketing.payment.application.command.dto.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.FailPaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.PaymentAttemptResult;
import com.example.limitedticketing.payment.application.command.service.PaymentCommandService;
import com.example.limitedticketing.payment.application.port.in.dto.ApprovePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.CreatePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.FailPaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttemptStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandFacade {

    private final PaymentCommandService paymentCommandService;

    public PaymentCommandFacade(PaymentCommandService paymentCommandService) {
        this.paymentCommandService = paymentCommandService;
    }

    public PaymentAttempt createPayment(CreatePaymentInput input) {
        PaymentAttemptResult result = paymentCommandService.createPayment(
            new CreatePaymentCommand(input.memberId(), input.purchaseId(), input.paymentRequestKey())
        );
        return PaymentAttempt.of(result.paymentId(), PaymentAttemptStatus.valueOf(result.status().name()), result.amount());
    }

    public PaymentAttempt approve(ApprovePaymentInput input) {
        PaymentAttemptResult result = paymentCommandService.approve(
            new ApprovePaymentCommand(input.memberId(), input.paymentId())
        );
        return PaymentAttempt.of(result.paymentId(), PaymentAttemptStatus.valueOf(result.status().name()), result.amount());
    }

    public PaymentAttempt fail(FailPaymentInput input) {
        PaymentAttemptResult result = paymentCommandService.fail(
            new FailPaymentCommand(input.memberId(), input.paymentId())
        );
        return PaymentAttempt.of(result.paymentId(), PaymentAttemptStatus.valueOf(result.status().name()), result.amount());
    }
}
