package com.example.limitedticketing.sale.infrastructure.adapter.out.module;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandPort;
import com.example.limitedticketing.inventory.application.port.in.dto.InitializeInventoryInput;
import com.example.limitedticketing.sale.application.port.out.InventoryInitializationPort;
import org.springframework.stereotype.Component;

@Component
public class SaleInventoryModuleAdapter implements InventoryInitializationPort {

    private final InventoryCommandPort inventoryCommandPort;

    public SaleInventoryModuleAdapter(InventoryCommandPort inventoryCommandPort) {
        this.inventoryCommandPort = inventoryCommandPort;
    }

    @Override
    public void initialize(Long saleProductId, long quantity) {
        inventoryCommandPort.initialize(new InitializeInventoryInput(saleProductId, quantity));
    }
}
