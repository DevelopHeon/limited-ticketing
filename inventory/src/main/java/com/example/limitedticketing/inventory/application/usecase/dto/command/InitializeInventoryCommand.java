package com.example.limitedticketing.inventory.application.usecase.dto.command;

public record InitializeInventoryCommand(Long saleProductId, long initialQuantity) {
}
