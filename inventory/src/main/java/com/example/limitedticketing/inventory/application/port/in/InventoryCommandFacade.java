package com.example.limitedticketing.inventory.application.port.in;

import com.example.limitedticketing.inventory.application.command.dto.ReserveInventoryCommand;
import com.example.limitedticketing.inventory.application.command.service.InventoryCommandService;
import com.example.limitedticketing.inventory.application.port.in.dto.ReserveInventoryInput;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandFacade {

    private final InventoryCommandService inventoryCommandService;

    public InventoryCommandFacade(InventoryCommandService inventoryCommandService) {
        this.inventoryCommandService = inventoryCommandService;
    }

    public void reserve(ReserveInventoryInput input) {
        inventoryCommandService.reserve(new ReserveInventoryCommand(input.saleProductId(), input.quantity()));
    }
}
