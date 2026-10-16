package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryCommandPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryQueryPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimPurchaseWaitingService implements ClaimPurchaseWaitingUseCase {

    private final PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort;
    private final PurchaseWaitingEntryCommandPort purchaseWaitingEntryCommandPort;
    private final Clock clock;
    private final int maxConcurrentProcessing;
    private final long processingTimeoutSeconds;

    public ClaimPurchaseWaitingService(
        PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort,
        PurchaseWaitingEntryCommandPort purchaseWaitingEntryCommandPort,
        Clock clock,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxConcurrentProcessing,
        @Value("${limited-ticketing.queue.worker.processing-timeout-seconds}") long processingTimeoutSeconds
    ) {
        this.purchaseWaitingEntryQueryPort = purchaseWaitingEntryQueryPort;
        this.purchaseWaitingEntryCommandPort = purchaseWaitingEntryCommandPort;
        this.clock = clock;
        this.maxConcurrentProcessing = maxConcurrentProcessing;
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    @Override
    @Transactional
    public Optional<PurchaseWaitingEntry> claim(ClaimPurchaseWaitingCommand command) {
        UUID id = command.waitingEntryId();
        UUID token = command.token();
        Optional<PurchaseWaitingEntry> entry = purchaseWaitingEntryQueryPort.findById(id);
        if (entry.isEmpty() || entry.get().getStatus() != PurchaseWaitingStatus.WAITING) {
            return Optional.empty();
        }
        purchaseWaitingEntryCommandPort.lockQueue(entry.get().getSaleProductId());
        if (purchaseWaitingEntryQueryPort.countProcessing(entry.get().getSaleProductId()) >= maxConcurrentProcessing) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return purchaseWaitingEntryCommandPort.claim(id, token, now, now.plusSeconds(processingTimeoutSeconds));
    }

    @Override
    @Transactional
    public Optional<PurchaseWaitingEntry> returnUnstartedClaim(ClaimPurchaseWaitingCommand command) {
        UUID id = command.waitingEntryId();
        UUID token = command.token();
        return purchaseWaitingEntryQueryPort.findByIdForUpdate(id).filter(entry -> entry.ownsAttempt(token)).map(entry -> {
            entry.returnToWaiting();
            return purchaseWaitingEntryCommandPort.save(entry);
        });
    }

}
