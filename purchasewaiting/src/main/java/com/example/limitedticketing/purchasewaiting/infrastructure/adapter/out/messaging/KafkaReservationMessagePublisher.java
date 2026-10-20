package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.messaging;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaReservationMessagePublisher implements ReservationMessagePublisherPort {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter acknowledgedCounter;
    private final Counter failedCounter;

    public KafkaReservationMessagePublisher(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        acknowledgedCounter = meterRegistry.counter("reservation.outbox.publish", "result", "acknowledged");
        failedCounter = meterRegistry.counter("reservation.outbox.publish", "result", "failed");
    }

    @Override
    public CompletableFuture<Void> publish(ReservationOutboxMessage message) {
        try {
            return kafkaTemplate.send(message.topic(), message.key(), message.payload())
                .thenAccept(result -> acknowledgedCounter.increment())
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        failedCounter.increment();
                    }
                });
        } catch (RuntimeException exception) {
            failedCounter.increment();
            return CompletableFuture.failedFuture(exception);
        }
    }
}
