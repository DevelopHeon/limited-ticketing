package com.example.limitedticketing.queue.application.port.in.dto;

public record EnqueueReservationInput(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
