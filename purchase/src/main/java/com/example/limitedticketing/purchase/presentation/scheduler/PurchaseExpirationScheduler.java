package com.example.limitedticketing.purchase.presentation.scheduler;

import com.example.limitedticketing.purchase.application.port.in.worker.PurchaseExpirationFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseExpirationScheduler {

    private final PurchaseExpirationFacade purchaseExpirationFacade;

    public PurchaseExpirationScheduler(PurchaseExpirationFacade purchaseExpirationFacade) {
        this.purchaseExpirationFacade = purchaseExpirationFacade;
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.purchase.expiration.fixed-delay:30000}",
        initialDelayString = "${limited-ticketing.purchase.expiration.initial-delay:30000}"
    )
    public void expireDuePurchases() {
        purchaseExpirationFacade.expireDuePurchases();
    }
}
