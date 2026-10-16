package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReserveWaitingPurchaseUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReserveWaitingPurchaseCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReserveWaitingPurchaseService implements ReserveWaitingPurchaseUseCase {

    private final PurchaseReservationPort purchaseReservationPort;

    public ReserveWaitingPurchaseService(PurchaseReservationPort purchaseReservationPort) {
        this.purchaseReservationPort = purchaseReservationPort;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long reserve(ReserveWaitingPurchaseCommand command) {
        return purchaseReservationPort.reserve(command.memberId(), command.saleProductId(), command.purchaseRequestKey());
    }
}
