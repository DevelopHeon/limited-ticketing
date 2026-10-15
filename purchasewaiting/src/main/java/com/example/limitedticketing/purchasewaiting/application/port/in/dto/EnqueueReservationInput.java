package com.example.limitedticketing.purchasewaiting.application.port.in.dto;

public record EnqueueReservationInput(Long memberId, Long saleProductId, String purchaseRequestKey) {
}
