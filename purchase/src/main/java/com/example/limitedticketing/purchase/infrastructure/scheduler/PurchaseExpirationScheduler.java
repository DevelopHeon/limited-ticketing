package com.example.limitedticketing.purchase.infrastructure.scheduler;

import com.example.limitedticketing.purchase.application.command.service.PurchaseExpirationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseExpirationScheduler {

    private final PurchaseExpirationService purchaseExpirationService;

    public PurchaseExpirationScheduler(PurchaseExpirationService purchaseExpirationService) {
        this.purchaseExpirationService = purchaseExpirationService;
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.purchase.expiration.fixed-delay:30000}",
        initialDelayString = "${limited-ticketing.purchase.expiration.initial-delay:30000}"
    )
    public void expireDuePurchases() {
        purchaseExpirationService.expireDuePurchases();
    }
}
