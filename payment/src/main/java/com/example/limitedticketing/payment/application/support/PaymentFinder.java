package com.example.limitedticketing.payment.application.support;

import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import org.springframework.stereotype.Component;

@Component
public class PaymentFinder {

    private final PaymentRepository paymentRepository;

    public PaymentFinder(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment getRequired(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(PaymentException::notFound);
    }
}
