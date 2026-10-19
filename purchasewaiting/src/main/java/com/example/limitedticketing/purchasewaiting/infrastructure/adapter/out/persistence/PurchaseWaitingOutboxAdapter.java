package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationJobEvent;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.record.ReservationOutboxRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseWaitingOutboxAdapter implements PurchaseWaitingOutboxPort {

    private final PurchaseWaitingOutboxJdbcRepository purchaseWaitingOutboxJdbcRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String reservationTopic;
    private final String deadLetterTopic;

    public PurchaseWaitingOutboxAdapter(PurchaseWaitingOutboxJdbcRepository purchaseWaitingOutboxJdbcRepository, ObjectMapper objectMapper, Clock clock,
        @Value("${limited-ticketing.kafka.reservation-topic:limited-ticketing.purchasewaiting.reservation.v1}") String reservationTopic,
        @Value("${limited-ticketing.kafka.dead-letter-topic:limited-ticketing.purchasewaiting.reservation.v1-dlt}") String deadLetterTopic) {
        this.purchaseWaitingOutboxJdbcRepository = purchaseWaitingOutboxJdbcRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.reservationTopic = reservationTopic;
        this.deadLetterTopic = deadLetterTopic;
    }

    @Override
    public void append(ReservationJobEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Reservation event cannot be serialized", exception);
        }
        LocalDateTime createdAt = LocalDateTime.ofInstant(event.occurredAt(), clock.getZone());
        String topic = ReservationJobEvent.REQUESTED.equals(event.eventType()) ? reservationTopic : deadLetterTopic;
        purchaseWaitingOutboxJdbcRepository.insertIfAbsent(new ReservationOutboxRecord(
            event.eventId(), event.waitingEntryId(), event.saleProductId(), event.processingToken(), event.eventType(),
            topic, event.saleProductId().toString(), payload, createdAt, 0));
    }

    @Override
    public boolean containsRequest(UUID eventId, UUID entryId, Long saleProductId, UUID token) {
        return purchaseWaitingOutboxJdbcRepository.containsRequest(eventId, entryId, saleProductId, token);
    }

    @Override
    public Optional<LocalDateTime> findPublishedAt(UUID entryId, UUID token) {
        return purchaseWaitingOutboxJdbcRepository.findPublishedAt(entryId, token);
    }

    @Override
    public List<ReservationOutboxMessage> findReadyForUpdate(LocalDateTime now, int limit) {
        return purchaseWaitingOutboxJdbcRepository.findReadyForUpdate(now, limit).stream()
            .map(record -> new ReservationOutboxMessage(record.eventId(), record.topic(), record.messageKey(),
                record.payload(), record.publishAttempts()))
            .toList();
    }

    @Override
    public void markPublished(UUID eventId, LocalDateTime now) {
        purchaseWaitingOutboxJdbcRepository.markPublished(eventId, now);
    }

    @Override
    public void recordFailure(UUID eventId, LocalDateTime nextAttempt, String failureCode) {
        purchaseWaitingOutboxJdbcRepository.recordFailure(eventId, nextAttempt, failureCode);
    }

}
