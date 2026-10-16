package com.example.limitedticketing.purchasewaiting.application.port.in;

import com.example.limitedticketing.purchasewaiting.application.port.in.dto.EnqueuePurchaseWaitingInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingOutput;

public interface PurchaseWaitingCommandPort {

    PurchaseWaitingOutput enqueue(EnqueuePurchaseWaitingInput input);
}
