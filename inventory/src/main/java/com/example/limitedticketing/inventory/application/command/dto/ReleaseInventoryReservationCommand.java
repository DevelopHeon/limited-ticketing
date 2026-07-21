package com.example.limitedticketing.inventory.application.command.dto;

public record ReleaseInventoryReservationCommand(Long saleProductId, long quantity) {
}
