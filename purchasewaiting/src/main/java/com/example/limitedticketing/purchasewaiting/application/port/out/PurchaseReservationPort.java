package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface PurchaseReservationPort {

    Long reserve(Long memberId, Long saleProductId, String purchaseRequestKey);
}
