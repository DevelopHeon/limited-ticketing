package com.example.limitedticketing.purchase.infrastructure.persistence.repository;

import com.example.limitedticketing.purchase.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPurchaseRepository extends JpaRepository<PurchaseJpaEntity, Long> {

    List<PurchaseJpaEntity> findByStatusAndExpiresAtLessThanEqual(PurchaseStatus status, LocalDateTime expiresAt);
}
