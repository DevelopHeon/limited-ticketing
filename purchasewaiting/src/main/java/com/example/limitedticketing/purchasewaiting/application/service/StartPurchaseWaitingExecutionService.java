package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.StartPurchaseWaitingExecutionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationAttemptStart;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartPurchaseWaitingExecutionService implements StartPurchaseWaitingExecutionUseCase {
    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    private final Clock clock;
    private final long leaseSeconds;

    public StartPurchaseWaitingExecutionService(PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingOutboxPort purchaseWaitingOutboxPort, Clock clock,
        @Value("${limited-ticketing.queue.worker.processing-timeout-seconds:30}") long leaseSeconds) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingOutboxPort = purchaseWaitingOutboxPort;
        this.clock = clock;
        this.leaseSeconds = leaseSeconds;
    }

    @Override
    @Transactional
    public ReservationAttemptStart start(ReservationExecutionCommand command) {
        if (!purchaseWaitingOutboxPort.containsRequest(command.eventId(), command.waitingEntryId(), command.saleProductId(), command.processingToken())) {
            throw new IllegalArgumentException("Reservation message does not match its durable outbox");
        }
        PurchaseWaitingEntry entry = purchaseWaitingEntryRepository.findByIdForUpdate(command.waitingEntryId()).orElseThrow();
        if (!entry.ownsDispatch(command.processingToken())) {
            return ReservationAttemptStart.IGNORED;
        }
        if (entry.getStatus() == PurchaseWaitingStatus.PROCESSING) {
            return ReservationAttemptStart.BUSY;
        }
        if (entry.getExecutionAttempts() >= PurchaseWaitingEntry.MAX_EXECUTION_ATTEMPTS) {
            return ReservationAttemptStart.EXHAUSTED;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        entry.beginProcessing(command.processingToken(), now, now.plusSeconds(leaseSeconds));
        purchaseWaitingEntryRepository.save(entry);
        return ReservationAttemptStart.STARTED;
    }
}
