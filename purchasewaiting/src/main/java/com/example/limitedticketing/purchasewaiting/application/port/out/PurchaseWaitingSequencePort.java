package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface PurchaseWaitingSequencePort {

    long nextSequence(Long saleProductId);
}
