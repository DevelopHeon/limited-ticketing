package com.example.limitedticketing.sale.infrastructure.persistence.repository;

import com.example.limitedticketing.sale.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductRepository extends JpaRepository<ProductJpaEntity, Long> {
}
