package com.example.limitedticketing.purchasewaiting.application.command.dto;

public record EnqueueReservationCommand(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
