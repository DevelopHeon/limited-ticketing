package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationQueueClaimService {

    private final ReservationQueueEntryRepository repository;
    private final Clock clock;
    private final int maxConcurrentProcessing;
    private final long processingTimeoutSeconds;

    public ReservationQueueClaimService(ReservationQueueEntryRepository repository, Clock clock,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxConcurrentProcessing,
        @Value("${limited-ticketing.queue.worker.processing-timeout-seconds}") long processingTimeoutSeconds) {
        this.repository = repository;
        this.clock = clock;
        this.maxConcurrentProcessing = maxConcurrentProcessing;
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    @Transactional
    public Optional<ReservationQueueEntry> claim(UUID id, UUID token) {
        Optional<ReservationQueueEntry> entry = repository.findById(id);
        if (entry.isEmpty() || entry.get().getStatus() != ReservationQueueStatus.WAITING) {
            return Optional.empty();
        }
        repository.lockQueue(entry.get().getSaleProductId());
        if (repository.countProcessing(entry.get().getSaleProductId()) >= maxConcurrentProcessing) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.claim(id, token, now, now.plusSeconds(processingTimeoutSeconds));
    }

    @Transactional
    public Optional<ReservationQueueEntry> returnUnstartedClaim(UUID id, UUID token) {
        return repository.findByIdForUpdate(id).filter(entry -> entry.ownsAttempt(token)).map(entry -> {
            entry.returnToWaiting();
            return repository.save(entry);
        });
    }
}
