package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface ReservationQueueAdmissionGuard {

    void check(Long memberId, Long saleProductId);
}
