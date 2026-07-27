package com.example.limitedticketing.queue.application.command.dto;

public record EnqueueReservationCommand(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
