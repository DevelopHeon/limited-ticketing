package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationJobEvent;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimPurchaseWaitingService implements ClaimPurchaseWaitingUseCase {

    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final Clock clock;
    private final PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    private final int globalConcurrentProcessing;
    private final int maxConcurrentProcessing;

    public ClaimPurchaseWaitingService(
        PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        Clock clock,
        PurchaseWaitingOutboxPort purchaseWaitingOutboxPort,
        @Value("${limited-ticketing.queue.worker.global-concurrent-processing:10}") int globalConcurrentProcessing,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxConcurrentProcessing
    ) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.clock = clock;
        this.purchaseWaitingOutboxPort = purchaseWaitingOutboxPort;
        this.globalConcurrentProcessing = globalConcurrentProcessing;
        this.maxConcurrentProcessing = maxConcurrentProcessing;
    }

    @Override
    @Transactional
    public Optional<PurchaseWaitingEntry> claim(ClaimPurchaseWaitingCommand command) {
        UUID id = command.waitingEntryId();
        UUID token = command.token();
        Optional<PurchaseWaitingEntry> entry = purchaseWaitingEntryRepository.findById(id);
        if (entry.isEmpty() || entry.get().getStatus() != PurchaseWaitingStatus.WAITING) {
            return Optional.empty();
        }
        purchaseWaitingEntryRepository.lockAdmission();
        purchaseWaitingEntryRepository.lockQueue(entry.get().getSaleProductId());
        if (purchaseWaitingEntryRepository.countInFlight() >= globalConcurrentProcessing) {
            return Optional.empty();
        }
        if (purchaseWaitingEntryRepository.countInFlight(entry.get().getSaleProductId()) >= maxConcurrentProcessing) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return purchaseWaitingEntryRepository.dispatch(id, token, now).map(dispatched -> {
            purchaseWaitingOutboxPort.append(new ReservationJobEvent(UUID.randomUUID(), ReservationJobEvent.REQUESTED,
                1, Instant.now(clock), dispatched.getId(), dispatched.getSaleProductId(), token, null));
            return dispatched;
        });
    }

}
