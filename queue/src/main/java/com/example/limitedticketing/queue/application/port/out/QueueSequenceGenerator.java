package com.example.limitedticketing.queue.application.port.out;

public interface QueueSequenceGenerator {

    long nextSequence(Long saleProductId);
}
