package com.example.limitedticketing.inventory.application.port.in.dto;

public record ConfirmInventoryReservationInput(Long saleProductId, long quantity) {
}
