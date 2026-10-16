package com.example.limitedticketing.inventory.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.inventory.infrastructure.adapter.out.persistence.entity.InventoryJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryPersistenceAdapter implements InventoryRepository {

    private final InventoryJpaRepository inventoryJpaRepository;

    public InventoryPersistenceAdapter(InventoryJpaRepository inventoryJpaRepository) {
        this.inventoryJpaRepository = inventoryJpaRepository;
    }

    @Override
    public Inventory save(Inventory inventory) {
        return inventoryJpaRepository.save(InventoryJpaEntity.from(inventory)).toDomain();
    }

    @Override
    public boolean reserveIfAvailable(Long saleProductId, long quantity) {
        return inventoryJpaRepository.reserveIfAvailable(saleProductId, quantity) == 1;
    }

    @Override
    public boolean confirmReservationIfAvailable(Long saleProductId, long quantity) {
        return inventoryJpaRepository.confirmReservationIfAvailable(saleProductId, quantity) == 1;
    }

    @Override
    public boolean releaseReservationIfAvailable(Long saleProductId, long quantity) {
        return inventoryJpaRepository.releaseReservationIfAvailable(saleProductId, quantity) == 1;
    }

    @Override
    public Optional<Inventory> findById(Long inventoryId) {
        return inventoryJpaRepository.findById(inventoryId).map(InventoryJpaEntity::toDomain);
    }

    @Override
    public Optional<Inventory> findBySaleProductId(Long saleProductId) {
        return inventoryJpaRepository.findBySaleProductId(saleProductId).map(InventoryJpaEntity::toDomain);
    }
}
