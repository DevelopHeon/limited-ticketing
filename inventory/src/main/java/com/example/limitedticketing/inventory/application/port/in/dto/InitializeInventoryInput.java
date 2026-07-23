package com.example.limitedticketing.inventory.application.port.in.dto;

public record InitializeInventoryInput(Long saleProductId, long initialQuantity) {
}
