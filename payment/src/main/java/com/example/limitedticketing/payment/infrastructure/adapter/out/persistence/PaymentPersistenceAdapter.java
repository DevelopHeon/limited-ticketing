package com.example.limitedticketing.payment.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.repository.IdempotentPayment;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import com.example.limitedticketing.payment.infrastructure.adapter.out.persistence.entity.PaymentJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    public PaymentPersistenceAdapter(PaymentJpaRepository paymentJpaRepository) {
        this.paymentJpaRepository = paymentJpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(PaymentJpaEntity.from(payment)).toDomain();
    }

    @Override
    public void transitionFromReady(Payment payment) {
        int updated = paymentJpaRepository.transitionIfReady(payment.getId(), payment.getStatus().name(), payment.getApprovedAt());
        if (updated == 0) {
            throw PaymentException.invalidState("Payment is no longer ready");
        }
    }

    @Override
    public IdempotentPayment saveIfAbsent(Payment payment) {
        int inserted = paymentJpaRepository.insertIfAbsent(
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
        return paymentJpaRepository.findById(paymentId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByPaymentRequestKey(String paymentRequestKey) {
        return paymentJpaRepository.findByPaymentRequestKey(paymentRequestKey).map(PaymentJpaEntity::toDomain);
    }
}
