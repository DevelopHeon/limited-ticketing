package com.example.limitedticketing.inventory.application.support;

import com.example.limitedticketing.inventory.domain.exception.InventoryException;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Component;

@Component
public class InventoryFinder {

    private final InventoryRepository inventoryRepository;

    public InventoryFinder(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Inventory getRequiredBySaleProductId(Long saleProductId) {
        return inventoryRepository.findBySaleProductId(saleProductId)
            .orElseThrow(InventoryException::notFound);
    }
}
