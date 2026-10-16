package com.example.limitedticketing.inventory.application.usecase.dto.command;

public record ReleaseInventoryReservationCommand(Long saleProductId, long quantity) {
}
