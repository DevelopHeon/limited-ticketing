package com.example.limitedticketing.payment.infrastructure.persistence;

import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.repository.IdempotentPayment;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import com.example.limitedticketing.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import com.example.limitedticketing.payment.infrastructure.persistence.repository.JpaPaymentRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final JpaPaymentRepository jpaPaymentRepository;

    public PaymentRepositoryAdapter(JpaPaymentRepository jpaPaymentRepository) {
        this.jpaPaymentRepository = jpaPaymentRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return jpaPaymentRepository.save(PaymentJpaEntity.from(payment)).toDomain();
    }

    @Override
    public IdempotentPayment saveIfAbsent(Payment payment) {
        int inserted = jpaPaymentRepository.insertIfAbsent(
            payment.getPurchaseId(),
            payment.getPaymentRequestKey(),
            payment.getAmount().getAmount(),
            payment.getStatus().name()
        );

        Payment persisted = findByPaymentRequestKey(payment.getPaymentRequestKey())
            .orElseThrow(PaymentException::persistenceFailure);

        return new IdempotentPayment(persisted, inserted == 1);
    }

    @Override
    public Optional<Payment> findById(Long paymentId) {
        return jpaPaymentRepository.findById(paymentId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByPaymentRequestKey(String paymentRequestKey) {
        return jpaPaymentRepository.findByPaymentRequestKey(paymentRequestKey).map(PaymentJpaEntity::toDomain);
    }
}
