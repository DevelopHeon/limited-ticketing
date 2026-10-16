package com.example.limitedticketing.purchase.application.usecase;

import com.example.limitedticketing.purchase.application.usecase.dto.command.MarkPurchasePaidCommand;

public interface MarkPurchasePaidUseCase {

    void markPaid(MarkPurchasePaidCommand command);
}
