package com.example.limitedticketing.payment.infrastructure.persistence.repository;

import com.example.limitedticketing.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPaymentRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByPaymentRequestKey(String paymentRequestKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update payments
        set status = :status,
            approved_at = :approvedAt
        where id = :paymentId
          and status = 'READY'
        """, nativeQuery = true)
    int transitionIfReady(
        @Param("paymentId") Long paymentId,
        @Param("status") String status,
        @Param("approvedAt") LocalDateTime approvedAt
    );

    @Modifying
    @Query(value = """
        insert into payments (
            purchase_id,
            payment_request_key,
            payment_amount,
            status
        ) values (
            :purchaseId,
            :paymentRequestKey,
            :paymentAmount,
            :status
        ) on conflict do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("purchaseId") Long purchaseId,
        @Param("paymentRequestKey") String paymentRequestKey,
        @Param("paymentAmount") BigDecimal paymentAmount,
        @Param("status") String status
    );
}
