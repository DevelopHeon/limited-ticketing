package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReleasePurchaseWaitingSlotUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReleasePurchaseWaitingSlotCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReleasePurchaseWaitingSlotService implements ReleasePurchaseWaitingSlotUseCase {
    private static final Logger log = LoggerFactory.getLogger(ReleasePurchaseWaitingSlotService.class);
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    public ReleasePurchaseWaitingSlotService(PurchaseWaitingQueuePort purchaseWaitingQueuePort) {
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
    }

    @Override
    public void release(ReleasePurchaseWaitingSlotCommand command) {
        try {
            purchaseWaitingQueuePort.releaseProcessing(command.saleProductId(), command.waitingEntryId(), command.processingToken());
        } catch (RuntimeException exception) {
            log.warn("Redis slot release deferred for entry {}", command.waitingEntryId(), exception);
        }
    }
}
