package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationAttemptStart;

public interface StartPurchaseWaitingExecutionUseCase {
    ReservationAttemptStart start(ReservationExecutionCommand command);
}
