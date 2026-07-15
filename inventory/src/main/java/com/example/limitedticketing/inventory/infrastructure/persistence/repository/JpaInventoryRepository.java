package com.example.limitedticketing.inventory.infrastructure.persistence.repository;

import com.example.limitedticketing.inventory.infrastructure.persistence.entity.InventoryJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInventoryRepository extends JpaRepository<InventoryJpaEntity, Long> {

    Optional<InventoryJpaEntity> findBySaleProductId(Long saleProductId);
}
