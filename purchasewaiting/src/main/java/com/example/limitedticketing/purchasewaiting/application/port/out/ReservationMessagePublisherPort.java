package com.example.limitedticketing.purchasewaiting.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface ReservationMessagePublisherPort {
    CompletableFuture<Void> publish(String topic, String key, String payload);
}
