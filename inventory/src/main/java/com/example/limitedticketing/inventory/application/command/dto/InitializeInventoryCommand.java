package com.example.limitedticketing.inventory.application.command.dto;

public record InitializeInventoryCommand(Long saleProductId, long initialQuantity) {
}
