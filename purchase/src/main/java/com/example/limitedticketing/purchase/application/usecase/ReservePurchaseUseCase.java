package com.example.limitedticketing.purchase.application.usecase;

import com.example.limitedticketing.purchase.application.usecase.dto.command.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchaseReservationResult;

public interface ReservePurchaseUseCase {

    PurchaseReservationResult reserve(ReservePurchaseCommand command);
}
