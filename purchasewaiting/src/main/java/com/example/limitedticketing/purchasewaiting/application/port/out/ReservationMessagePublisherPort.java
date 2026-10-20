package com.example.limitedticketing.purchasewaiting.application.port.out;

import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import java.util.concurrent.CompletableFuture;

public interface ReservationMessagePublisherPort {
    CompletableFuture<Void> publish(ReservationOutboxMessage message);
}
