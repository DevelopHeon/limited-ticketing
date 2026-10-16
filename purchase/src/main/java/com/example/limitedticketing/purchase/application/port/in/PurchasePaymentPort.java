package com.example.limitedticketing.purchase.application.port.in;

import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseForPaymentInput;
import com.example.limitedticketing.purchase.application.port.in.dto.MarkPurchasePaidInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentTarget;

public interface PurchasePaymentPort {

    PurchasePaymentTarget findPaymentTarget(FindPurchaseForPaymentInput input);

    void markPaid(MarkPurchasePaidInput input);
}
