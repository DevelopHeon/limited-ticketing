package com.example.limitedticketing.payment.domain.repository;

import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    void transitionFromReady(Payment payment);

    IdempotentPayment saveIfAbsent(Payment payment);

    Optional<Payment> findById(Long paymentId);

    default Payment getRequiredById(Long paymentId) {
        return findById(paymentId).orElseThrow(PaymentException::notFound);
    }

    Optional<Payment> findByPaymentRequestKey(String paymentRequestKey);
}
