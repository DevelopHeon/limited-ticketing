package com.example.limitedticketing.purchase.infrastructure.persistence.repository;

import com.example.limitedticketing.purchase.infrastructure.persistence.entity.PurchaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPurchaseRepository extends JpaRepository<PurchaseJpaEntity, Long> {
}
