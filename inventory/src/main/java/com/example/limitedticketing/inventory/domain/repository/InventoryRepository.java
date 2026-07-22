package com.example.limitedticketing.inventory.domain.repository;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import java.util.Optional;

public interface InventoryRepository {

    Inventory save(Inventory inventory);

    boolean reserveIfAvailable(Long saleProductId, long quantity);

    boolean confirmReservationIfAvailable(Long saleProductId, long quantity);

    boolean releaseReservationIfAvailable(Long saleProductId, long quantity);

    Optional<Inventory> findById(Long inventoryId);

    Optional<Inventory> findBySaleProductId(Long saleProductId);
}
