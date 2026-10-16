package com.example.limitedticketing.purchase.application.port.in.worker;

import com.example.limitedticketing.purchase.application.command.service.PurchaseExpirationService;
import org.springframework.stereotype.Component;

@Component
public class PurchaseExpirationFacade {

    private final PurchaseExpirationService expirationService;

    public PurchaseExpirationFacade(PurchaseExpirationService expirationService) {
        this.expirationService = expirationService;
    }

    public void expireDuePurchases() {
        expirationService.expireDuePurchases();
    }
}
