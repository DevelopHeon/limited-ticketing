package com.example.limitedticketing.inventory.application.command.dto;

public record ReserveInventoryCommand(Long saleProductId, long quantity) {
}
