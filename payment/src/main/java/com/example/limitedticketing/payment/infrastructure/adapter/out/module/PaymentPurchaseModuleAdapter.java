package com.example.limitedticketing.payment.infrastructure.adapter.out.module;

import com.example.limitedticketing.payment.application.port.out.PurchasePaymentPort;
import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentStatus;
import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentTarget;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseForPaymentInput;
import com.example.limitedticketing.purchase.application.port.in.dto.MarkPurchasePaidInput;
import org.springframework.stereotype.Component;

@Component
public class PaymentPurchaseModuleAdapter implements PurchasePaymentPort {

    private final com.example.limitedticketing.purchase.application.port.in.PurchasePaymentPort purchasePaymentPort;

    public PaymentPurchaseModuleAdapter(com.example.limitedticketing.purchase.application.port.in.PurchasePaymentPort purchasePaymentPort) {
        this.purchasePaymentPort = purchasePaymentPort;
    }

    @Override
    public PurchasePaymentTarget findPaymentTarget(Long purchaseId) {
        com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentTarget result =
            purchasePaymentPort.findPaymentTarget(new FindPurchaseForPaymentInput(purchaseId));
        return new PurchasePaymentTarget(result.purchaseId(), result.memberId(), result.saleProductId(),
            result.amount(), result.quantity(), result.expiresAt(), PurchasePaymentStatus.valueOf(result.status().name()));
    }

    @Override
    public void markPaid(Long purchaseId) {
        purchasePaymentPort.markPaid(new MarkPurchasePaidInput(purchaseId));
    }
}
