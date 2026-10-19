package com.example.limitedticketing.purchasewaiting.application.facade;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingReservationPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobOutcome;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecordPurchaseWaitingFailureUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.StartPurchaseWaitingExecutionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationAttemptStart;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationExecutionResult;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PurchaseWaitingReservationFacade implements PurchaseWaitingReservationPort {
    private static final Logger log = LoggerFactory.getLogger(PurchaseWaitingReservationFacade.class);
    private final StartPurchaseWaitingExecutionUseCase startPurchaseWaitingExecutionUseCase;
    private final ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase;
    private final RecordPurchaseWaitingFailureUseCase recordPurchaseWaitingFailureUseCase;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    public PurchaseWaitingReservationFacade(StartPurchaseWaitingExecutionUseCase startPurchaseWaitingExecutionUseCase,
        ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase, RecordPurchaseWaitingFailureUseCase recordPurchaseWaitingFailureUseCase,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort) {
        this.startPurchaseWaitingExecutionUseCase = startPurchaseWaitingExecutionUseCase;
        this.processPurchaseWaitingUseCase = processPurchaseWaitingUseCase;
        this.recordPurchaseWaitingFailureUseCase = recordPurchaseWaitingFailureUseCase;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
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
        Optional<PurchaseWaitingEntry> completed;
        try {
            completed = processPurchaseWaitingUseCase.process(new ProcessPurchaseWaitingCommand(input.waitingEntryId(), input.processingToken()));
        } catch (RuntimeException exception) {
            log.warn("Reservation execution failed for entry {}", input.waitingEntryId(), exception);
            return finishFailure(command);
        }
        completed.ifPresent(this::release);
        return ReservationJobOutcome.COMPLETED;
    }

    private ReservationJobOutcome finishFailure(ReservationExecutionCommand command) {
        ReservationExecutionResult result = recordPurchaseWaitingFailureUseCase.record(command);
        if (result == ReservationExecutionResult.COMPLETED) {
            try {
                purchaseWaitingQueuePort.releaseProcessing(command.saleProductId(), command.waitingEntryId(), command.processingToken());
            } catch (RuntimeException exception) {
                log.warn("Redis slot release deferred for entry {}", command.waitingEntryId(), exception);
            }
        }
        return ReservationJobOutcome.valueOf(result.name());
    }

    private void release(PurchaseWaitingEntry entry) {
        try {
            purchaseWaitingQueuePort.releaseProcessing(entry);
        } catch (RuntimeException exception) {
            log.warn("Redis slot release deferred for entry {}", entry.getId(), exception);
        }
    }
}
