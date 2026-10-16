package com.example.limitedticketing.purchase.infrastructure.adapter.out.messaging;

import com.example.limitedticketing.purchase.application.port.out.PurchaseEventPublisherPort;
import com.example.limitedticketing.purchase.domain.event.PurchaseReserved;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SpringPurchaseEventPublisherAdapter implements PurchaseEventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringPurchaseEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(PurchaseReserved event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Purchase events must be published in a transaction");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applicationEventPublisher.publishEvent(event);
            }
        });
    }
}
