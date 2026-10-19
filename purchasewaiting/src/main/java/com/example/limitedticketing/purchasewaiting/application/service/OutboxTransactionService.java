package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import com.example.limitedticketing.purchasewaiting.application.usecase.OutboxTransactionUseCase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxTransactionService implements OutboxTransactionUseCase {
    private final PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    private final Clock clock;
    private final long claimLeaseSeconds;

    public OutboxTransactionService(PurchaseWaitingOutboxPort purchaseWaitingOutboxPort, Clock clock,
        @Value("${limited-ticketing.kafka.outbox.claim-lease-seconds:30}") long claimLeaseSeconds) {
        if (claimLeaseSeconds <= 0) {
            throw new IllegalArgumentException("Outbox claim lease must be positive");
        }
        this.purchaseWaitingOutboxPort = purchaseWaitingOutboxPort;
        this.clock = clock;
        this.claimLeaseSeconds = claimLeaseSeconds;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public List<ReservationOutboxClaim> claimReady(int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        return purchaseWaitingOutboxPort.claimReady(UUID.randomUUID(), now, now.plusSeconds(claimLeaseSeconds), limit);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean complete(ReservationOutboxClaim claim) {
        return purchaseWaitingOutboxPort.markPublished(claim.message().eventId(), claim.claimId(), LocalDateTime.now(clock));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean recordFailure(ReservationOutboxClaim claim, Throwable failure) {
        LocalDateTime now = LocalDateTime.now(clock);
        long delay = Math.min(30, 1L << Math.min(5, Math.max(0, claim.message().attempts() - 1)));
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return purchaseWaitingOutboxPort.recordFailure(claim.message().eventId(), claim.claimId(), now, now.plusSeconds(delay), cause.getClass().getSimpleName());
    }
}
