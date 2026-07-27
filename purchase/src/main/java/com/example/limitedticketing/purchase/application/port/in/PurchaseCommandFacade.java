package com.example.limitedticketing.purchase.application.port.in;

import com.example.limitedticketing.purchase.application.command.dto.PurchaseReservationResult;
import com.example.limitedticketing.purchase.application.command.dto.CancelPurchaseCommand;
import com.example.limitedticketing.purchase.application.command.dto.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.command.service.PurchaseCommandService;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.CancelPurchaseInput;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import org.springframework.stereotype.Component;

@Component
public class PurchaseCommandFacade {

    private final PurchaseCommandService purchaseCommandService;

    public PurchaseCommandFacade(PurchaseCommandService purchaseCommandService) {
        this.purchaseCommandService = purchaseCommandService;
    }

    public PurchaseReservation reserve(ReservePurchaseInput input) {
        PurchaseReservationResult result = purchaseCommandService.reserve(
            new ReservePurchaseCommand(input.memberId(), input.saleProductId(), input.purchaseRequestKey())
        );

        return PurchaseReservation.of(result.purchaseId(), result.status().name(), result.expiresAt());
    }

    public void cancel(CancelPurchaseInput input) {
        purchaseCommandService.cancel(new CancelPurchaseCommand(input.purchaseId(), input.memberId()));
    }
}
