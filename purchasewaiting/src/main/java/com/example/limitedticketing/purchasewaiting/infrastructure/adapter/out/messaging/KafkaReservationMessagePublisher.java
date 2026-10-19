package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.messaging;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(6, TimeUnit.SECONDS);
            acknowledgedCounter.increment();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failedCounter.increment();
            throw new IllegalStateException("Reservation publish interrupted", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            failedCounter.increment();
            throw new IllegalStateException("Reservation publish was not acknowledged", exception);
        }
    }
}
