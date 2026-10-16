package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.module;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandPort;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationPort;
import org.springframework.stereotype.Component;

@Component
public class PurchaseModuleAdapter implements PurchaseReservationPort {

    private final PurchaseCommandPort purchaseCommandPort;

    public PurchaseModuleAdapter(
        PurchaseCommandPort purchaseCommandPort
    ) {
        this.purchaseCommandPort = purchaseCommandPort;
    }

    @Override
    public Long reserve(Long memberId, Long saleProductId, String purchaseRequestKey) {
        return purchaseCommandPort.reserve(new ReservePurchaseInput(memberId, saleProductId, purchaseRequestKey)).purchaseId();
    }
}
