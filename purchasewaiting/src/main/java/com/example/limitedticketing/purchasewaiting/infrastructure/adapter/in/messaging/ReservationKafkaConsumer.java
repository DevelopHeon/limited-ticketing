package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingReservationPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Profile("!api")
public class ReservationKafkaConsumer {
    public static final String LISTENER_ID = "purchasewaiting-reservation";
    private final PurchaseWaitingReservationPort purchaseWaitingReservationPort;
    private final ObjectMapper objectMapper;
    private final Timer executionTimer;
    private final Counter completedCounter;
    private final Counter retryCounter;
    private final Counter rejectedCounter;

    public ReservationKafkaConsumer(PurchaseWaitingReservationPort purchaseWaitingReservationPort,
        ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.purchaseWaitingReservationPort = purchaseWaitingReservationPort;
        this.objectMapper = objectMapper;
        executionTimer = meterRegistry.timer("reservation.consumer.duration");
        completedCounter = meterRegistry.counter("reservation.consumer.records", "outcome", "completed");
        retryCounter = meterRegistry.counter("reservation.consumer.records", "outcome", "retry");
        rejectedCounter = meterRegistry.counter("reservation.consumer.records", "outcome", "rejected");
    }

    @KafkaListener(id = LISTENER_ID, topics = "${limited-ticketing.kafka.reservation-topic:limited-ticketing.purchasewaiting.reservation.v1}",
        groupId = "${limited-ticketing.kafka.group-id:purchasewaiting-reservation-v1}", containerFactory = "reservationKafkaListenerContainerFactory")
    public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        ReservationJobInput input = parse(record);
        ReservationJobOutcome outcome;
        try {
            outcome = executionTimer.record(() -> purchaseWaitingReservationPort.execute(input));
        } catch (IllegalArgumentException exception) {
            rejectedCounter.increment();
            throw new InvalidReservationMessageException("Reservation message has no matching durable job", exception);
        }
        if (outcome == ReservationJobOutcome.RETRY) {
            retryCounter.increment();
            acknowledgment.nack(Duration.ofSeconds(1));
        } else {
            completedCounter.increment();
            acknowledgment.acknowledge();
        }
    }

    private ReservationJobInput parse(ConsumerRecord<String, String> record) {
        try {
            JsonNode json = objectMapper.readTree(record.value());
            if (json == null || !json.isObject() || !json.path("schemaVersion").isInt()
                || json.path("schemaVersion").intValue() != 1
                || !"PurchaseReservationRequested".equals(json.path("eventType").textValue())
                || !json.path("saleProductId").isIntegralNumber() || !json.path("saleProductId").canConvertToLong()
                || json.path("saleProductId").longValue() <= 0) {
                throw new IllegalArgumentException("Unsupported reservation message schema");
            }
            Long saleProductId = json.path("saleProductId").longValue();
            if (!saleProductId.toString().equals(record.key())) {
                throw new IllegalArgumentException("Reservation key differs from its sale product");
            }
            Instant.parse(json.path("occurredAt").textValue());
            return new ReservationJobInput(UUID.fromString(json.path("eventId").textValue()),
                UUID.fromString(json.path("waitingEntryId").textValue()), saleProductId,
                UUID.fromString(json.path("processingToken").textValue()));
        } catch (Exception exception) {
            rejectedCounter.increment();
            throw new InvalidReservationMessageException("Invalid reservation message contract", exception);
        }
    }
}
