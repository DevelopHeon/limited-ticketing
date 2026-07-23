package com.example.limitedticketing.sale.infrastructure.persistence.repository;

import com.example.limitedticketing.sale.infrastructure.persistence.entity.SaleProductJpaEntity;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSaleProductRepository extends JpaRepository<SaleProductJpaEntity, Long> {

    Page<SaleProductJpaEntity> findByStatus(SaleStatus status, Pageable pageable);
}
