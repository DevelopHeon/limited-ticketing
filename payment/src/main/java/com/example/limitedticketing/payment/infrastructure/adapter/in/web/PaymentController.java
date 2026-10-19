package com.example.limitedticketing.payment.infrastructure.adapter.in.web;

import com.example.limitedticketing.payment.application.port.in.PaymentCommandPort;
import com.example.limitedticketing.payment.application.port.in.dto.ApprovePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.CreatePaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.FailPaymentInput;
import com.example.limitedticketing.payment.application.port.in.dto.PaymentAttempt;
import com.example.limitedticketing.payment.infrastructure.adapter.in.web.request.CreatePaymentRequest;
import com.example.limitedticketing.payment.infrastructure.adapter.in.web.response.PaymentAttemptResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!worker")
@RequestMapping("/api")
public class PaymentController {

    private final PaymentCommandPort paymentCommandPort;

    public PaymentController(PaymentCommandPort paymentCommandPort) {
        this.paymentCommandPort = paymentCommandPort;
    }

    @PostMapping("/purchases/{purchaseId}/payments")
    public ResponseEntity<ApiResponse<PaymentAttemptResponse>> createPayment(
        @PathVariable("purchaseId") Long purchaseId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId,
        @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentAttempt attempt = paymentCommandPort.createPayment(
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
            PaymentAttemptResponse.from(paymentCommandPort.approve(new ApprovePaymentInput(memberId, paymentId)))
        );
    }

    @PostMapping("/payments/{paymentId}/fail")
    public ApiResponse<PaymentAttemptResponse> fail(
        @PathVariable("paymentId") Long paymentId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ApiResponse.success(
            PaymentAttemptResponse.from(paymentCommandPort.fail(new FailPaymentInput(memberId, paymentId)))
        );
    }
}
