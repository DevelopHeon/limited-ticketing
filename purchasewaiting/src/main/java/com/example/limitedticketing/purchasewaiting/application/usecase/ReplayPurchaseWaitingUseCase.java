package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;
import java.util.UUID;

public interface ReplayPurchaseWaitingUseCase {
    PurchaseWaitingResult replay(UUID waitingEntryId);
}
