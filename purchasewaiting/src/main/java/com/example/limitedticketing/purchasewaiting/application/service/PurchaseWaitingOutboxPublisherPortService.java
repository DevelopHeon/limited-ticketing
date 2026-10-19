package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingOutboxPublisherPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.PublishReservationOutboxUseCase;
import org.springframework.stereotype.Service;

@Service
public class PurchaseWaitingOutboxPublisherPortService implements PurchaseWaitingOutboxPublisherPort {
    private final PublishReservationOutboxUseCase publishReservationOutboxUseCase;

    public PurchaseWaitingOutboxPublisherPortService(PublishReservationOutboxUseCase publishReservationOutboxUseCase) {
        this.publishReservationOutboxUseCase = publishReservationOutboxUseCase;
    }

    @Override
    public void publishReadyMessages() {
        publishReservationOutboxUseCase.publishReady();
    }
}
