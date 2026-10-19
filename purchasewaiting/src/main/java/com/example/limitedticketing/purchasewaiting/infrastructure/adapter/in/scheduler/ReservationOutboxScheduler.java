package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.scheduler;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingOutboxPublisherPort;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!api")
public class ReservationOutboxScheduler {
    private final PurchaseWaitingOutboxPublisherPort purchaseWaitingOutboxPublisherPort;

    public ReservationOutboxScheduler(PurchaseWaitingOutboxPublisherPort purchaseWaitingOutboxPublisherPort) {
        this.purchaseWaitingOutboxPublisherPort = purchaseWaitingOutboxPublisherPort;
    }

    @Scheduled(fixedDelayString = "${limited-ticketing.kafka.publisher-delay:100}",
        initialDelayString = "${limited-ticketing.kafka.publisher-initial-delay:1000}")
    public void publishReadyMessages() {
        purchaseWaitingOutboxPublisherPort.publishReadyMessages();
    }
}
