package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.module;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandPort;
import com.example.limitedticketing.purchase.application.port.in.PurchaseRequestQueryPort;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseByRequestInput;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationLookupPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PurchaseModuleAdapter implements PurchaseReservationPort, PurchaseReservationLookupPort {

    private final PurchaseCommandPort purchaseCommandPort;
    private final PurchaseRequestQueryPort purchaseRequestQueryPort;

    public PurchaseModuleAdapter(
        PurchaseCommandPort purchaseCommandPort, PurchaseRequestQueryPort purchaseRequestQueryPort
    ) {
        this.purchaseCommandPort = purchaseCommandPort;
        this.purchaseRequestQueryPort = purchaseRequestQueryPort;
    }

    @Override
    public Optional<Long> findPurchaseId(Long memberId, Long saleProductId, String purchaseRequestKey) {
        return purchaseRequestQueryPort.findPurchaseId(new FindPurchaseByRequestInput(memberId, saleProductId, purchaseRequestKey));
    }

    @Override
    public Long reserve(Long memberId, Long saleProductId, String purchaseRequestKey) {
        return purchaseCommandPort.reserve(new ReservePurchaseInput(memberId, saleProductId, purchaseRequestKey)).purchaseId();
    }
}
