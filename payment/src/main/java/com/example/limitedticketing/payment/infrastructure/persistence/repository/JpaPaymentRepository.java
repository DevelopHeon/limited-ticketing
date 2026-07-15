package com.example.limitedticketing.payment.infrastructure.persistence.repository;

import com.example.limitedticketing.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {
}
