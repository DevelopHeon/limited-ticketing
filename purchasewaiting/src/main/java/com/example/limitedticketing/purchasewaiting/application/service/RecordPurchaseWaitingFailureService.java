package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationLookupPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationJobEvent;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecordPurchaseWaitingFailureUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationExecutionResult;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordPurchaseWaitingFailureService implements RecordPurchaseWaitingFailureUseCase {
    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    private final PurchaseReservationLookupPort purchaseReservationLookupPort;
    private final Clock clock;

    public RecordPurchaseWaitingFailureService(PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingOutboxPort purchaseWaitingOutboxPort, PurchaseReservationLookupPort purchaseReservationLookupPort, Clock clock) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingOutboxPort = purchaseWaitingOutboxPort;
        this.purchaseReservationLookupPort = purchaseReservationLookupPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationExecutionResult record(ReservationExecutionCommand command) {
        PurchaseWaitingEntry entry = purchaseWaitingEntryRepository.findByIdForUpdate(command.waitingEntryId()).orElseThrow();
        if (!entry.ownsDispatch(command.processingToken())) {
            return ReservationExecutionResult.COMPLETED;
        }
        if (entry.getExecutionAttempts() < PurchaseWaitingEntry.MAX_EXECUTION_ATTEMPTS) {
            entry.retryDispatched();
            purchaseWaitingEntryRepository.save(entry);
            return ReservationExecutionResult.RETRY;
        }
        Optional<Long> purchaseId = purchaseReservationLookupPort.findPurchaseId(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey());
        if (purchaseId.isPresent()) {
            entry.completeReservation(purchaseId.get(), LocalDateTime.now(clock));
        } else {
            entry.failExecution(LocalDateTime.now(clock));
            purchaseWaitingOutboxPort.append(new ReservationJobEvent(UUID.randomUUID(), ReservationJobEvent.FAILED,
                1, Instant.now(clock), entry.getId(), entry.getSaleProductId(), command.processingToken(), entry.getFailureCode()));
        }
        purchaseWaitingEntryRepository.save(entry);
        return ReservationExecutionResult.COMPLETED;
    }
}
