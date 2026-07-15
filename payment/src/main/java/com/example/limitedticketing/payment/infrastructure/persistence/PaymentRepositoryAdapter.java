package com.example.limitedticketing.payment.infrastructure.persistence;

import com.example.limitedticketing.payment.domain.model.Payment;
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
    public Optional<Payment> findById(Long paymentId) {
        return jpaPaymentRepository.findById(paymentId).map(PaymentJpaEntity::toDomain);
    }
}
