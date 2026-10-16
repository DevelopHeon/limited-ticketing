package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.RestorePurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestorePurchaseWaitingService implements RestorePurchaseWaitingUseCase {

    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    public RestorePurchaseWaitingService(
        PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort
    ) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreWaiting(UUID id) {
        purchaseWaitingEntryRepository.findByIdForUpdateSkipLocked(id)
            .filter(entry -> entry.getStatus() == PurchaseWaitingStatus.WAITING)
            .ifPresent(purchaseWaitingQueuePort::restoreWaiting);
    }
}
