package com.example.limitedticketing.inventory.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class InventoryException extends DomainException {

    private InventoryException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static InventoryException notFound() {
        return new InventoryException(DomainErrorType.NOT_FOUND, "INVENTORY_NOT_FOUND", "Inventory not found");
    }

    public static InventoryException invalid(String message) {
        return new InventoryException(DomainErrorType.INVALID_REQUEST, "INVALID_INVENTORY", message);
    }

    public static InventoryException insufficientAvailableQuantity() {
        return new InventoryException(DomainErrorType.CONFLICT, "INSUFFICIENT_INVENTORY", "Insufficient inventory");
    }

    public static InventoryException insufficientReservedQuantity() {
        return new InventoryException(
            DomainErrorType.CONFLICT,
            "INSUFFICIENT_RESERVED_INVENTORY",
            "Reserved inventory is insufficient"
        );
    }
}
