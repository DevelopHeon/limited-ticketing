package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.util.Optional;

public interface ClaimPurchaseWaitingUseCase {

    Optional<PurchaseWaitingEntry> claim(ClaimPurchaseWaitingCommand command);
}
