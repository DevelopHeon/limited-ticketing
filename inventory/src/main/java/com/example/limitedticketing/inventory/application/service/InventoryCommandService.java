package com.example.limitedticketing.inventory.application.service;

import com.example.limitedticketing.inventory.application.usecase.ConfirmInventoryReservationUseCase;
import com.example.limitedticketing.inventory.application.usecase.InitializeInventoryUseCase;
import com.example.limitedticketing.inventory.application.usecase.ReleaseInventoryReservationUseCase;
import com.example.limitedticketing.inventory.application.usecase.ReserveInventoryUseCase;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ConfirmInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.InitializeInventoryCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ReleaseInventoryReservationCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ReserveInventoryCommand;
import com.example.limitedticketing.inventory.domain.exception.InventoryException;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCommandService implements
    InitializeInventoryUseCase,
    ReserveInventoryUseCase,
    ConfirmInventoryReservationUseCase,
    ReleaseInventoryReservationUseCase {

    private final InventoryRepository inventoryRepository;

    public InventoryCommandService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
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
        inventoryRepository.getRequiredBySaleProductId(saleProductId);
        throw insufficientQuantityException;
    }
}
