package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryQueryPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.RestorePurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestorePurchaseWaitingService implements RestorePurchaseWaitingUseCase {

    private final PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    public RestorePurchaseWaitingService(
        PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort
    ) {
        this.purchaseWaitingEntryQueryPort = purchaseWaitingEntryQueryPort;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreWaiting(UUID id) {
        purchaseWaitingEntryQueryPort.findByIdForUpdateSkipLocked(id)
            .filter(entry -> entry.getStatus() == PurchaseWaitingStatus.WAITING)
            .ifPresent(purchaseWaitingQueuePort::restoreWaiting);
    }
}
