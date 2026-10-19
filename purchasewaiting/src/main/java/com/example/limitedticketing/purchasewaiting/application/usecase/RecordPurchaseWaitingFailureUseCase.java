package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationExecutionResult;

public interface RecordPurchaseWaitingFailureUseCase {
    ReservationExecutionResult record(ReservationExecutionCommand command);
}
