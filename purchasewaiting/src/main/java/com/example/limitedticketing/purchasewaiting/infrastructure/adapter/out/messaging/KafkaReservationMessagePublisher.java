package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.messaging;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.infrastructure.metrics.ReservationMessagingMetrics;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaReservationMessagePublisher implements ReservationMessagePublisherPort {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ReservationMessagingMetrics reservationMessagingMetrics;

    public KafkaReservationMessagePublisher(KafkaTemplate<String, String> kafkaTemplate, ReservationMessagingMetrics reservationMessagingMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.reservationMessagingMetrics = reservationMessagingMetrics;
    }

    @Override
    public CompletableFuture<Void> publish(ReservationOutboxMessage message) {
        try {
            return kafkaTemplate.send(message.topic(), message.key(), message.payload())
                .thenAccept(result -> reservationMessagingMetrics.recordPublishAcknowledged())
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        reservationMessagingMetrics.recordPublishFailed();
                    }
                });
        } catch (RuntimeException exception) {
            reservationMessagingMetrics.recordPublishFailed();
            return CompletableFuture.failedFuture(exception);
        }
    }
}
