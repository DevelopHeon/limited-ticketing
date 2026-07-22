package com.example.limitedticketing.inventory.infrastructure.persistence;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.inventory.infrastructure.persistence.entity.InventoryJpaEntity;
import com.example.limitedticketing.inventory.infrastructure.persistence.repository.JpaInventoryRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepositoryAdapter implements InventoryRepository {

    private final JpaInventoryRepository jpaInventoryRepository;

    public InventoryRepositoryAdapter(JpaInventoryRepository jpaInventoryRepository) {
        this.jpaInventoryRepository = jpaInventoryRepository;
    }

    @Override
    public Inventory save(Inventory inventory) {
        return jpaInventoryRepository.save(InventoryJpaEntity.from(inventory)).toDomain();
    }

    @Override
    public boolean reserveIfAvailable(Long saleProductId, long quantity) {
        return jpaInventoryRepository.reserveIfAvailable(saleProductId, quantity) == 1;
    }

    @Override
    public boolean confirmReservationIfAvailable(Long saleProductId, long quantity) {
        return jpaInventoryRepository.confirmReservationIfAvailable(saleProductId, quantity) == 1;
    }

    @Override
    public boolean releaseReservationIfAvailable(Long saleProductId, long quantity) {
        return jpaInventoryRepository.releaseReservationIfAvailable(saleProductId, quantity) == 1;
    }

    @Override
    public Optional<Inventory> findById(Long inventoryId) {
        return jpaInventoryRepository.findById(inventoryId).map(InventoryJpaEntity::toDomain);
    }

    @Override
    public Optional<Inventory> findBySaleProductId(Long saleProductId) {
        return jpaInventoryRepository.findBySaleProductId(saleProductId).map(InventoryJpaEntity::toDomain);
    }
}
