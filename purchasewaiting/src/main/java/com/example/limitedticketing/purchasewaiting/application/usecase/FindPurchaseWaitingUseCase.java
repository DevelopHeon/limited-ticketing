package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.query.FindPurchaseWaitingQuery;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;

public interface FindPurchaseWaitingUseCase {

    PurchaseWaitingResult find(FindPurchaseWaitingQuery query);
}
