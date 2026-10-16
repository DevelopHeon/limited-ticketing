package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface PurchaseWaitingAdmissionPort {

    void check(Long memberId, Long saleProductId);
}
