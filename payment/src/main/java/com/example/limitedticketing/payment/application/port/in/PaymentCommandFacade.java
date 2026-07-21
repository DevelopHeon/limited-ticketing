package com.example.limitedticketing.payment.application.port.in;

import com.example.limitedticketing.payment.application.command.dto.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.FailPaymentCommand;
import com.example.limitedticketing.payment.application.command.service.PaymentCommandService;
import com.example.limitedticketing.payment.application.port.in.dto.ApprovePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.CreatePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.FailPaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandFacade {

    private final PaymentCommandService paymentCommandService;

    public PaymentCommandFacade(PaymentCommandService paymentCommandService) {
        this.paymentCommandService = paymentCommandService;
    }

    public PaymentAttempt createPayment(CreatePaymentInput input) {
        return PaymentAttempt.from(paymentCommandService.createPayment(
            new CreatePaymentCommand(input.memberId(), input.purchaseId(), input.paymentRequestKey())
        ));
    }

    public PaymentAttempt approve(ApprovePaymentInput input) {
        return PaymentAttempt.from(paymentCommandService.approve(
            new ApprovePaymentCommand(input.memberId(), input.paymentId())
        ));
    }

    public PaymentAttempt fail(FailPaymentInput input) {
        return PaymentAttempt.from(paymentCommandService.fail(
            new FailPaymentCommand(input.memberId(), input.paymentId())
        ));
    }
}
