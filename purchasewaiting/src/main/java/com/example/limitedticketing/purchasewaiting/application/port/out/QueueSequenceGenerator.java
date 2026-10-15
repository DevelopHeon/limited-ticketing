package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface QueueSequenceGenerator {

    long nextSequence(Long saleProductId);
}
