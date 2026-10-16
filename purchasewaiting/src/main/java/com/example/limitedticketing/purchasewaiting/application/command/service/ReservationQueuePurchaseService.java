package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationQueuePurchaseService {

    private final PurchaseCommandFacade purchaseFacade;

    public ReservationQueuePurchaseService(PurchaseCommandFacade purchaseFacade) {
        this.purchaseFacade = purchaseFacade;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PurchaseReservation reserve(ReservePurchaseInput input) {
        return purchaseFacade.reserve(input);
    }
}
