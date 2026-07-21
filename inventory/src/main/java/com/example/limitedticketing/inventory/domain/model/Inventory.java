package com.example.limitedticketing.inventory.domain.model;

import com.example.limitedticketing.inventory.domain.exception.InventoryException;
public class Inventory {

    private final Long id;
    private final Long saleProductId;
    private long availableQuantity;
    private long reservedQuantity;
    private long soldQuantity;

    private Inventory(Long id, Long saleProductId, long availableQuantity, long reservedQuantity, long soldQuantity) {
        this.id = id;
        if (saleProductId == null || availableQuantity < 0 || reservedQuantity < 0 || soldQuantity < 0) {
            throw InventoryException.invalid("Sale product ID and non-negative quantity are required");
        }
        this.saleProductId = saleProductId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.soldQuantity = soldQuantity;
    }

    public static Inventory initialize(Long saleProductId, long initialQuantity) {
        return new Inventory(null, saleProductId, initialQuantity, 0, 0);
    }

    public static Inventory reconstitute(
            Long id,
            Long saleProductId,
            long availableQuantity,
            long reservedQuantity,
            long soldQuantity
    ) {
        return new Inventory(id, saleProductId, availableQuantity, reservedQuantity, soldQuantity);
    }

    public void reserve(long quantity) {
        validateQuantity(quantity);
        if (availableQuantity < quantity) {
            throw InventoryException.insufficientAvailableQuantity();
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    public void confirmReservation(long quantity) {
        validateReservedQuantity(quantity);
        reservedQuantity -= quantity;
        soldQuantity += quantity;
    }

    public void releaseReservation(long quantity) {
        validateReservedQuantity(quantity);
        reservedQuantity -= quantity;
        availableQuantity += quantity;
    }

    public Long getSaleProductId() {
        return saleProductId;
    }

    public Long getId() {
        return id;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public long getSoldQuantity() {
        return soldQuantity;
    }

    private void validateQuantity(long quantity) {
        if (quantity <= 0) {
            throw InventoryException.invalid("Quantity must be positive");
        }
    }

    private void validateReservedQuantity(long quantity) {
        validateQuantity(quantity);
        if (reservedQuantity < quantity) {
            throw InventoryException.insufficientReservedQuantity();
        }
    }
}
