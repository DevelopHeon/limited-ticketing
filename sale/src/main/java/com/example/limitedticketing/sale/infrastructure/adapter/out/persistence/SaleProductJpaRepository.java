package com.example.limitedticketing.sale.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.infrastructure.adapter.out.persistence.entity.SaleProductJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleProductJpaRepository extends JpaRepository<SaleProductJpaEntity, Long> {

    Page<SaleProductJpaEntity> findByStatus(SaleStatus status, Pageable pageable);
}
