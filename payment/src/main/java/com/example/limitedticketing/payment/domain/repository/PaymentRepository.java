package com.example.limitedticketing.payment.domain.repository;

import com.example.limitedticketing.payment.domain.model.Payment;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    IdempotentPayment saveIfAbsent(Payment payment);

    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByPaymentRequestKey(String paymentRequestKey);
}
