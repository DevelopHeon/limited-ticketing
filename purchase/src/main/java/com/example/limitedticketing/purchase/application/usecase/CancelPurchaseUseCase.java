package com.example.limitedticketing.purchase.application.usecase;

import com.example.limitedticketing.purchase.application.usecase.dto.command.CancelPurchaseCommand;

public interface CancelPurchaseUseCase {

    void cancel(CancelPurchaseCommand command);
}
