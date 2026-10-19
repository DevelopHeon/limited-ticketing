package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.in.PurchaseWaitingReplayPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingReplayOutput;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReplayPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PurchaseWaitingReplayPortService implements PurchaseWaitingReplayPort {
    private final ReplayPurchaseWaitingUseCase replayPurchaseWaitingUseCase;

    public PurchaseWaitingReplayPortService(ReplayPurchaseWaitingUseCase replayPurchaseWaitingUseCase) {
        this.replayPurchaseWaitingUseCase = replayPurchaseWaitingUseCase;
    }

    @Override
    public PurchaseWaitingReplayOutput replay(UUID waitingEntryId) {
        PurchaseWaitingEntry entry = replayPurchaseWaitingUseCase.replay(waitingEntryId);
        return new PurchaseWaitingReplayOutput(entry.getId(), entry.getStatus().name(), entry.getPurchaseId());
    }
}
