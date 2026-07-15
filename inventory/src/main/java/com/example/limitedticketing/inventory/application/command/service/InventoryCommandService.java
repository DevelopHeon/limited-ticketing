package com.example.limitedticketing.inventory.application.command.service;

import com.example.limitedticketing.inventory.application.command.dto.ReserveInventoryCommand;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;

    public InventoryCommandService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public void reserve(ReserveInventoryCommand command) {
        Inventory inventory = inventoryRepository.findBySaleProductId(command.saleProductId())
            .orElseThrow(() -> new NoSuchElementException("Inventory not found"));

        inventory.reserve(command.quantity());
        inventoryRepository.save(inventory);
    }
}
