package com.example.limitedticketing.payment.infrastructure.adapter.out.module;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandPort;
import com.example.limitedticketing.inventory.application.port.in.dto.ConfirmInventoryReservationInput;
import com.example.limitedticketing.payment.application.port.out.InventoryConfirmationPort;
import org.springframework.stereotype.Component;

@Component
public class PaymentInventoryModuleAdapter implements InventoryConfirmationPort {

    private final InventoryCommandPort inventoryCommandPort;

    public PaymentInventoryModuleAdapter(InventoryCommandPort inventoryCommandPort) {
        this.inventoryCommandPort = inventoryCommandPort;
    }

    @Override
    public void confirmReservation(Long saleProductId, long quantity) {
        inventoryCommandPort.confirmReservation(new ConfirmInventoryReservationInput(saleProductId, quantity));
    }
}
