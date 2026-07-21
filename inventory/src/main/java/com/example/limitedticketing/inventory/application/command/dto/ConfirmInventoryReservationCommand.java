package com.example.limitedticketing.inventory.application.command.dto;

public record ConfirmInventoryReservationCommand(Long saleProductId, long quantity) {
}
