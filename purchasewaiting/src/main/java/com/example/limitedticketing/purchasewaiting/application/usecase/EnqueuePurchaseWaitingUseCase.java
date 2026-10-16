package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.EnqueuePurchaseWaitingCommand;
import java.util.UUID;

public interface EnqueuePurchaseWaitingUseCase {

    UUID enqueue(EnqueuePurchaseWaitingCommand command);
}
