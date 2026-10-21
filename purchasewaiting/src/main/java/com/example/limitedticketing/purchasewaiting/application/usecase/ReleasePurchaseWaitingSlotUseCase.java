package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReleasePurchaseWaitingSlotCommand;

public interface ReleasePurchaseWaitingSlotUseCase {
    void release(ReleasePurchaseWaitingSlotCommand command);
}
