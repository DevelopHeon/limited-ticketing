package com.example.limitedticketing.purchasewaiting.application.port.in;

import com.example.limitedticketing.purchasewaiting.application.port.in.dto.FindPurchaseWaitingInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingOutput;

public interface PurchaseWaitingQueryPort {

    PurchaseWaitingOutput find(FindPurchaseWaitingInput input);
}
