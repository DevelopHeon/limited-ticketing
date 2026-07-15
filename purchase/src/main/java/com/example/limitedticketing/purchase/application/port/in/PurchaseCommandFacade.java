package com.example.limitedticketing.purchase.application.port.in;

import com.example.limitedticketing.purchase.application.command.dto.PurchaseReservationResult;
import com.example.limitedticketing.purchase.application.command.dto.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.command.service.PurchaseCommandService;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
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
            new ReservePurchaseCommand(input.memberId(), input.saleProductId())
        );

        return new PurchaseReservation(result.purchaseId(), result.status(), result.expiresAt());
    }
}
