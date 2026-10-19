package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.application.usecase.PublishReservationOutboxUseCase;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishReservationOutboxService implements PublishReservationOutboxUseCase {
    private static final Logger log = LoggerFactory.getLogger(PublishReservationOutboxService.class);
    private final PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    private final ReservationMessagePublisherPort reservationMessagePublisherPort;
    private final Clock clock;

    public PublishReservationOutboxService(PurchaseWaitingOutboxPort purchaseWaitingOutboxPort,
        ReservationMessagePublisherPort reservationMessagePublisherPort, Clock clock) {
        this.purchaseWaitingOutboxPort = purchaseWaitingOutboxPort;
        this.reservationMessagePublisherPort = reservationMessagePublisherPort;
        this.clock = clock;
    }

    @Override
    @Transactional(timeout = 90)
    public void publishReady() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (ReservationOutboxMessage message : purchaseWaitingOutboxPort.findReadyForUpdate(now, 10)) {
            try {
                reservationMessagePublisherPort.publish(message.topic(), message.key(), message.payload());
            } catch (RuntimeException exception) {
                long delay = Math.min(30, 1L << Math.min(5, message.attempts()));
                purchaseWaitingOutboxPort.recordFailure(message.eventId(), LocalDateTime.now(clock).plusSeconds(delay), exception.getClass().getSimpleName());
                log.warn("Reservation event {} remains pending after publish failure", message.eventId(), exception);
                continue;
            }
            purchaseWaitingOutboxPort.markPublished(message.eventId(), LocalDateTime.now(clock));
        }
    }
}
