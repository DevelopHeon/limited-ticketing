package com.example.limitedticketing.purchasewaiting.application.usecase;

import java.util.UUID;

public interface RestorePurchaseWaitingUseCase {

    void restoreWaiting(UUID waitingEntryId);
}
