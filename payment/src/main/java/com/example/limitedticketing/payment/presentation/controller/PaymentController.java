package com.example.limitedticketing.payment.presentation.controller;

import com.example.limitedticketing.payment.application.port.in.PaymentCommandFacade;
import com.example.limitedticketing.payment.application.port.in.dto.ApprovePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.CreatePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.FailPaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;
import com.example.limitedticketing.payment.presentation.request.CreatePaymentRequest;
import com.example.limitedticketing.payment.presentation.response.PaymentAttemptResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentCommandFacade paymentCommandFacade;

    public PaymentController(PaymentCommandFacade paymentCommandFacade) {
        this.paymentCommandFacade = paymentCommandFacade;
    }

    @PostMapping("/purchases/{purchaseId}/payments")
    public ResponseEntity<ApiResponse<PaymentAttemptResponse>> createPayment(
        @PathVariable("purchaseId") Long purchaseId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId,
        @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentAttempt attempt = paymentCommandFacade.createPayment(
            new CreatePaymentInput(memberId, purchaseId, request.paymentRequestKey())
        );
        PaymentAttemptResponse response = PaymentAttemptResponse.from(attempt);

        return ResponseEntity.created(URI.create("/api/payments/" + response.paymentId()))
            .body(ApiResponse.success(response));
    }

    @PostMapping("/payments/{paymentId}/approve")
    public ApiResponse<PaymentAttemptResponse> approve(
        @PathVariable("paymentId") Long paymentId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ApiResponse.success(
            PaymentAttemptResponse.from(paymentCommandFacade.approve(new ApprovePaymentInput(memberId, paymentId)))
        );
    }

    @PostMapping("/payments/{paymentId}/fail")
    public ApiResponse<PaymentAttemptResponse> fail(
        @PathVariable("paymentId") Long paymentId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ApiResponse.success(
            PaymentAttemptResponse.from(paymentCommandFacade.fail(new FailPaymentInput(memberId, paymentId)))
        );
    }
}
