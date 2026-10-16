package com.example.limitedticketing.purchase.application.port.in;

import com.example.limitedticketing.purchase.application.port.in.dto.CancelPurchaseInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;

public interface PurchaseCommandPort {

    PurchaseReservation reserve(ReservePurchaseInput input);

    void cancel(CancelPurchaseInput input);
}
