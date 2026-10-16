package com.example.limitedticketing.payment.application.port.out;

import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentTarget;

public interface PurchasePaymentPort {

    PurchasePaymentTarget findPaymentTarget(Long purchaseId);

    void markPaid(Long purchaseId);
}
