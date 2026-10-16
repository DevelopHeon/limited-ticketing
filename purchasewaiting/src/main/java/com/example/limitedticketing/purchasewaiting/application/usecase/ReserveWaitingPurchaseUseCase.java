package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReserveWaitingPurchaseCommand;

public interface ReserveWaitingPurchaseUseCase {

    Long reserve(ReserveWaitingPurchaseCommand command);
}
