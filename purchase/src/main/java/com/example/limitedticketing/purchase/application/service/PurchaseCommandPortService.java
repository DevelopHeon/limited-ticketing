package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandPort;
import com.example.limitedticketing.purchase.application.port.in.dto.CancelPurchaseInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchase.application.usecase.CancelPurchaseUseCase;
import com.example.limitedticketing.purchase.application.usecase.ReservePurchaseUseCase;
import com.example.limitedticketing.purchase.application.usecase.dto.command.CancelPurchaseCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.command.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchaseReservationResult;
import org.springframework.stereotype.Service;

@Service
public class PurchaseCommandPortService implements PurchaseCommandPort {

    private final ReservePurchaseUseCase reservePurchaseUseCase;
    private final CancelPurchaseUseCase cancelPurchaseUseCase;

    public PurchaseCommandPortService(
        ReservePurchaseUseCase reservePurchaseUseCase,
        CancelPurchaseUseCase cancelPurchaseUseCase
    ) {
        this.reservePurchaseUseCase = reservePurchaseUseCase;
        this.cancelPurchaseUseCase = cancelPurchaseUseCase;
    }

    public PurchaseReservation reserve(ReservePurchaseInput input) {
        PurchaseReservationResult result = reservePurchaseUseCase.reserve(
            new ReservePurchaseCommand(input.memberId(), input.saleProductId(), input.purchaseRequestKey())
        );

        return PurchaseReservation.of(result.purchaseId(), result.status().name(), result.expiresAt());
    }

    public void cancel(CancelPurchaseInput input) {
        cancelPurchaseUseCase.cancel(new CancelPurchaseCommand(input.purchaseId(), input.memberId()));
    }
}
