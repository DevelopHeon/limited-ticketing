package com.example.limitedticketing.purchase.infrastructure.adapter.out.module;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandPort;
import com.example.limitedticketing.inventory.application.port.in.dto.ReleaseInventoryReservationInput;
import com.example.limitedticketing.inventory.application.port.in.dto.ReserveInventoryInput;
import com.example.limitedticketing.purchase.application.port.out.InventoryReservationPort;
import org.springframework.stereotype.Component;

@Component
public class PurchaseInventoryModuleAdapter implements InventoryReservationPort {

    private final InventoryCommandPort inventoryCommandPort;

    public PurchaseInventoryModuleAdapter(InventoryCommandPort inventoryCommandPort) {
        this.inventoryCommandPort = inventoryCommandPort;
    }

    @Override
    public void reserve(Long saleProductId, long quantity) {
        inventoryCommandPort.reserve(new ReserveInventoryInput(saleProductId, quantity));
    }

    @Override
    public void releaseReservation(Long saleProductId, long quantity) {
        inventoryCommandPort.releaseReservation(new ReleaseInventoryReservationInput(saleProductId, quantity));
    }
}
