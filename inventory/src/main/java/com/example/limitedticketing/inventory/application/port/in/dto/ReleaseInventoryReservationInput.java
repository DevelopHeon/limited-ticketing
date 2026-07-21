package com.example.limitedticketing.inventory.application.port.in.dto;

public record ReleaseInventoryReservationInput(Long saleProductId, long quantity) {
}
