package com.example.limitedticketing.purchase.infrastructure.adapter.in.scheduler;

import com.example.limitedticketing.purchase.application.port.in.worker.PurchaseExpirationPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseExpirationScheduler {

    private final PurchaseExpirationPort purchaseExpirationPort;

    public PurchaseExpirationScheduler(PurchaseExpirationPort purchaseExpirationPort) {
        this.purchaseExpirationPort = purchaseExpirationPort;
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.purchase.expiration.fixed-delay:30000}",
        initialDelayString = "${limited-ticketing.purchase.expiration.initial-delay:30000}"
    )
    public void expireDuePurchases() {
        purchaseExpirationPort.expireDuePurchases();
    }
}
