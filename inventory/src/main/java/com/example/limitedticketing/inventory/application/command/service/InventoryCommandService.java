package com.example.limitedticketing.inventory.application.command.service;

import com.example.limitedticketing.inventory.application.command.dto.ConfirmInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.command.dto.ReleaseInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.command.dto.ReserveInventoryCommand;
import com.example.limitedticketing.inventory.application.support.InventoryFinder;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final InventoryFinder inventoryFinder;

    public InventoryCommandService(InventoryRepository inventoryRepository, InventoryFinder inventoryFinder) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryFinder = inventoryFinder;
    }

    public void reserve(ReserveInventoryCommand command) {
        Inventory inventory = inventoryFinder.getRequiredBySaleProductId(command.saleProductId());

        inventory.reserve(command.quantity());
        inventoryRepository.save(inventory);
    }

    public void confirmReservation(ConfirmInventoryReservationCommand command) {
        Inventory inventory = inventoryFinder.getRequiredBySaleProductId(command.saleProductId());

        inventory.confirmReservation(command.quantity());
        inventoryRepository.save(inventory);
    }

    public void releaseReservation(ReleaseInventoryReservationCommand command) {
        Inventory inventory = inventoryFinder.getRequiredBySaleProductId(command.saleProductId());

        inventory.releaseReservation(command.quantity());
        inventoryRepository.save(inventory);
    }
}
