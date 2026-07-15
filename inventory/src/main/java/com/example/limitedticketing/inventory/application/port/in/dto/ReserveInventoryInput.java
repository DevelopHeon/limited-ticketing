package com.example.limitedticketing.inventory.application.port.in.dto;

public record ReserveInventoryInput(Long saleProductId, long quantity) {
}
