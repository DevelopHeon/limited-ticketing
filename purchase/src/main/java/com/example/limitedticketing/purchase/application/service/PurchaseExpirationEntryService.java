package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.port.in.worker.PurchaseExpirationPort;
import com.example.limitedticketing.purchase.application.usecase.ExpirePurchasesUseCase;
import org.springframework.stereotype.Service;

@Service
public class PurchaseExpirationEntryService implements PurchaseExpirationPort {

    private final ExpirePurchasesUseCase expirePurchasesUseCase;

    public PurchaseExpirationEntryService(ExpirePurchasesUseCase expirePurchasesUseCase) {
        this.expirePurchasesUseCase = expirePurchasesUseCase;
    }

    public void expireDuePurchases() {
        expirePurchasesUseCase.expireDuePurchases();
    }
}
