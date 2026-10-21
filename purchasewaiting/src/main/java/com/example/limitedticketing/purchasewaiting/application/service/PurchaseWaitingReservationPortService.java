package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingReservationPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobOutcome;
import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecordPurchaseWaitingFailureUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReleasePurchaseWaitingSlotUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.StartPurchaseWaitingExecutionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReleasePurchaseWaitingSlotCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationAttemptStart;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PurchaseWaitingReservationPortService implements PurchaseWaitingReservationPort {
    private static final Logger log = LoggerFactory.getLogger(PurchaseWaitingReservationPortService.class);
    private final StartPurchaseWaitingExecutionUseCase startPurchaseWaitingExecutionUseCase;
    private final ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase;
    private final RecordPurchaseWaitingFailureUseCase recordPurchaseWaitingFailureUseCase;
    private final ReleasePurchaseWaitingSlotUseCase releasePurchaseWaitingSlotUseCase;

    public PurchaseWaitingReservationPortService(StartPurchaseWaitingExecutionUseCase startPurchaseWaitingExecutionUseCase,
        ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase, RecordPurchaseWaitingFailureUseCase recordPurchaseWaitingFailureUseCase,
        ReleasePurchaseWaitingSlotUseCase releasePurchaseWaitingSlotUseCase) {
        this.startPurchaseWaitingExecutionUseCase = startPurchaseWaitingExecutionUseCase;
        this.processPurchaseWaitingUseCase = processPurchaseWaitingUseCase;
        this.recordPurchaseWaitingFailureUseCase = recordPurchaseWaitingFailureUseCase;
        this.releasePurchaseWaitingSlotUseCase = releasePurchaseWaitingSlotUseCase;
    }

    @Override
    public ReservationJobOutcome execute(ReservationJobInput input) {
        ReservationExecutionCommand command = new ReservationExecutionCommand(input.eventId(), input.waitingEntryId(), input.saleProductId(), input.processingToken());
        ReservationAttemptStart start = startPurchaseWaitingExecutionUseCase.start(command);
        if (start == ReservationAttemptStart.IGNORED) {
            return ReservationJobOutcome.COMPLETED;
        }
        if (start == ReservationAttemptStart.BUSY) {
            return ReservationJobOutcome.RETRY;
        }
        if (start == ReservationAttemptStart.EXHAUSTED) {
            return finishFailure(command);
        }
        boolean completed;
        try {
            completed = processPurchaseWaitingUseCase.process(new ProcessPurchaseWaitingCommand(input.waitingEntryId(), input.processingToken())).isPresent();
        } catch (RuntimeException exception) {
            log.warn("Reservation execution failed for entry {}", input.waitingEntryId(), exception);
            return finishFailure(command);
        }
        if (completed) {
            release(command);
        }
        return ReservationJobOutcome.COMPLETED;
    }

    private ReservationJobOutcome finishFailure(ReservationExecutionCommand command) {
        ReservationExecutionResult result = recordPurchaseWaitingFailureUseCase.record(command);
        if (result == ReservationExecutionResult.COMPLETED) {
            release(command);
        }
        return ReservationJobOutcome.valueOf(result.name());
    }

    private void release(ReservationExecutionCommand command) {
        releasePurchaseWaitingSlotUseCase.release(new ReleasePurchaseWaitingSlotCommand(
            command.saleProductId(), command.waitingEntryId(), command.processingToken()));
    }
}
