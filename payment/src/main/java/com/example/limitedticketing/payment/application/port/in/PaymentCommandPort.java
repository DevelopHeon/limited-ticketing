package com.example.limitedticketing.payment.application.port.in;

import com.example.limitedticketing.payment.application.port.in.dto.ApprovePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.CreatePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.FailPaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;

public interface PaymentCommandPort {

    PaymentAttempt createPayment(CreatePaymentInput input);

    PaymentAttempt approve(ApprovePaymentInput input);

    PaymentAttempt fail(FailPaymentInput input);
}
