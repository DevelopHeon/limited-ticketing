package com.example.limitedticketing.sale.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.sale.infrastructure.adapter.out.persistence.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
}
