package com.example.limitedticketing.inventory.application.usecase.dto.command;

public record ReserveInventoryCommand(Long saleProductId, long quantity) {
}
