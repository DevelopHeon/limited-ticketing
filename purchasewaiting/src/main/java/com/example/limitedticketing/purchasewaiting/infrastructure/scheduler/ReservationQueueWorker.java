package com.example.limitedticketing.purchasewaiting.infrastructure.scheduler;

import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueClaimService;
import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueProcessingService;
import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueRecoveryService;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(ReservationQueueWorker.class);

    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final ReservationQueueStore reservationQueueStore;
    private final ReservationQueueClaimService reservationQueueClaimService;
    private final ReservationQueueProcessingService reservationQueueProcessingService;
    private final ReservationQueueRecoveryService reservationQueueRecoveryService;
    private final Executor reservationQueueTaskExecutor;
    private final Clock clock;
    private final int maxConcurrentProcessing;
    private final int claimBatchSize;
    private final long processingTimeoutSeconds;

    public ReservationQueueWorker(
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        ReservationQueueStore reservationQueueStore,
        ReservationQueueClaimService reservationQueueClaimService,
        ReservationQueueProcessingService reservationQueueProcessingService,
        ReservationQueueRecoveryService reservationQueueRecoveryService,
        @Qualifier("reservationQueueTaskExecutor") Executor reservationQueueTaskExecutor,
        Clock clock,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxConcurrentProcessing,
        @Value("${limited-ticketing.queue.worker.claim-batch-size}") int claimBatchSize,
        @Value("${limited-ticketing.queue.worker.processing-timeout-seconds}") long processingTimeoutSeconds
    ) {
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.reservationQueueStore = reservationQueueStore;
        this.reservationQueueClaimService = reservationQueueClaimService;
        this.reservationQueueProcessingService = reservationQueueProcessingService;
        this.reservationQueueRecoveryService = reservationQueueRecoveryService;
        this.reservationQueueTaskExecutor = reservationQueueTaskExecutor;
        this.clock = clock;
        this.maxConcurrentProcessing = maxConcurrentProcessing;
        this.claimBatchSize = claimBatchSize;
        this.processingTimeoutSeconds = processingTimeoutSeconds;
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.queue.worker.fixed-delay}",
        initialDelayString = "${limited-ticketing.queue.worker.initial-delay}"
    )
    public void dispatchWaitingEntries() {
        Instant now = Instant.now(clock);
        reservationQueueEntryRepository.findSaleProductIdsWithWaitingEntries()
            .forEach(saleProductId -> reservationQueueStore.claimNext(
                saleProductId,
                maxConcurrentProcessing,
                claimBatchSize,
                now
            ).forEach(queueEntryId -> dispatchClaimedEntry(saleProductId, queueEntryId)));
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.queue.worker.recovery-fixed-delay}",
        initialDelayString = "${limited-ticketing.queue.worker.recovery-initial-delay}"
    )
    public void recoverStaleEntries() {
        reservationQueueRecoveryService.recover(processingTimeoutSeconds);
    }

    private void dispatchClaimedEntry(Long saleProductId, UUID queueEntryId) {
        reservationQueueClaimService.claim(queueEntryId)
            .ifPresentOrElse(
                entry -> reservationQueueTaskExecutor.execute(() -> process(entry)),
                () -> reservationQueueStore.releaseProcessing(saleProductId, queueEntryId)
            );
    }

    private void process(ReservationQueueEntry entry) {
        try {
            ReservationQueueEntry completedEntry = reservationQueueProcessingService.process(entry.getId());
            reservationQueueStore.releaseProcessing(completedEntry);
        } catch (RuntimeException exception) {
            log.error("Reservation queue processing failed for entry {}", entry.getId(), exception);
        }
    }
}
