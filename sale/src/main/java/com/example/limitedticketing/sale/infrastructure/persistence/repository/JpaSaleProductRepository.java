package com.example.limitedticketing.sale.infrastructure.persistence.repository;

import com.example.limitedticketing.sale.infrastructure.persistence.entity.SaleProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSaleProductRepository extends JpaRepository<SaleProductJpaEntity, Long> {
}
