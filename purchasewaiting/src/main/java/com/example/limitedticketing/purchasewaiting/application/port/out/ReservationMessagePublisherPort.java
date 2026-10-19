package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface ReservationMessagePublisherPort {
    void publish(String topic, String key, String payload);
}
