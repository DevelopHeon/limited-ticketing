package com.example.limitedticketing.inventory.application.command.service;

import com.example.limitedticketing.inventory.application.command.dto.ConfirmInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.command.dto.InitializeInventoryCommand;
import com.example.limitedticketing.inventory.application.command.dto.ReleaseInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.command.dto.ReserveInventoryCommand;
import com.example.limitedticketing.inventory.application.support.InventoryFinder;
import com.example.limitedticketing.inventory.domain.exception.InventoryException;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final InventoryFinder inventoryFinder;

    public InventoryCommandService(InventoryRepository inventoryRepository, InventoryFinder inventoryFinder) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryFinder = inventoryFinder;
    }

    @Transactional
    public void initialize(InitializeInventoryCommand command) {
        validatePositiveQuantity(command.initialQuantity());
        if (inventoryRepository.findBySaleProductId(command.saleProductId()).isPresent()) {
            throw InventoryException.alreadyInitialized();
        }
        inventoryRepository.save(Inventory.initialize(command.saleProductId(), command.initialQuantity()));
    }

    @Transactional
    public void reserve(ReserveInventoryCommand command) {
        validatePositiveQuantity(command.quantity());
        if (!inventoryRepository.reserveIfAvailable(command.saleProductId(), command.quantity())) {
            throwReservationFailure(command.saleProductId(), InventoryException.insufficientAvailableQuantity());
        }
    }

    @Transactional
    public void confirmReservation(ConfirmInventoryReservationCommand command) {
        validatePositiveQuantity(command.quantity());
        if (!inventoryRepository.confirmReservationIfAvailable(command.saleProductId(), command.quantity())) {
            throwReservationFailure(command.saleProductId(), InventoryException.insufficientReservedQuantity());
        }
    }

    @Transactional
    public void releaseReservation(ReleaseInventoryReservationCommand command) {
        validatePositiveQuantity(command.quantity());
        if (!inventoryRepository.releaseReservationIfAvailable(command.saleProductId(), command.quantity())) {
            throwReservationFailure(command.saleProductId(), InventoryException.insufficientReservedQuantity());
        }
    }

    private void validatePositiveQuantity(long quantity) {
        if (quantity <= 0) {
            throw InventoryException.invalid("Quantity must be positive");
        }
    }

    private void throwReservationFailure(Long saleProductId, InventoryException insufficientQuantityException) {
        inventoryFinder.getRequiredBySaleProductId(saleProductId);
        throw insufficientQuantityException;
    }
}
