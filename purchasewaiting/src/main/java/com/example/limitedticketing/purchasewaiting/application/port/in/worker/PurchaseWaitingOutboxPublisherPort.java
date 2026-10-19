package com.example.limitedticketing.purchasewaiting.application.port.in.worker;

public interface PurchaseWaitingOutboxPublisherPort {
    void publishReadyMessages();
}
