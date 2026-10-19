package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.util.UUID;

public interface ReplayPurchaseWaitingUseCase {
    PurchaseWaitingEntry replay(UUID waitingEntryId);
}
