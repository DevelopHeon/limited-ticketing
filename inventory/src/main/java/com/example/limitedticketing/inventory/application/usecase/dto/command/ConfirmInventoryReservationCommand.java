package com.example.limitedticketing.inventory.application.usecase.dto.command;

public record ConfirmInventoryReservationCommand(Long saleProductId, long quantity) {
}
