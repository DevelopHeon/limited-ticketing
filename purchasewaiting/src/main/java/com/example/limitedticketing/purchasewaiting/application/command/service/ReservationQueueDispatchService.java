package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueClaim;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueTaskExecutor;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReservationQueueDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ReservationQueueDispatchService.class);

    private final ReservationQueueEntryRepository repository;
    private final ReservationQueueStore store;
    private final ReservationQueueClaimService claimService;
    private final ReservationQueueProcessingService processingService;
    private final ReservationQueueRecoveryService recoveryService;
    private final ReservationQueueTaskExecutor executor;
    private final Clock clock;
    private final int maxPerSale;
    private final int batchSize;
    private final AtomicInteger nextProduct = new AtomicInteger();

    public ReservationQueueDispatchService(ReservationQueueEntryRepository repository, ReservationQueueStore store,
        ReservationQueueClaimService claimService, ReservationQueueProcessingService processingService,
        ReservationQueueRecoveryService recoveryService, ReservationQueueTaskExecutor executor, Clock clock,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxPerSale,
        @Value("${limited-ticketing.queue.worker.claim-batch-size}") int batchSize) {
        this.repository = repository;
        this.store = store;
        this.claimService = claimService;
        this.processingService = processingService;
        this.recoveryService = recoveryService;
        this.executor = executor;
        this.clock = clock;
        this.maxPerSale = maxPerSale;
        this.batchSize = batchSize;
    }

    public void dispatchWaitingEntries() {
        int available = executor.availableSlots();
        if (available <= 0) {
            return;
        }
        List<Long> sales = repository.findSaleProductIdsWithWaitingEntries().stream().sorted().toList();
        if (sales.isEmpty()) {
            return;
        }
        int first = Math.floorMod(nextProduct.getAndIncrement(), sales.size());
        for (int offset = 0; offset < sales.size() && available > 0; offset++) {
            Long saleId = sales.get((first + offset) % sales.size());
            List<ReservationQueueClaim> claims = store.claimNext(saleId, maxPerSale,
                Math.min(batchSize, available), Instant.now(clock));
            for (ReservationQueueClaim claim : claims) {
                dispatch(saleId, claim);
                available--;
            }
        }
    }

    public void recoverStaleEntries() {
        recoveryService.recover();
    }

    private void dispatch(Long saleId, ReservationQueueClaim claim) {
        try {
            claimService.claim(claim.queueEntryId(), claim.token()).ifPresentOrElse(entry -> {
                try {
                    executor.execute(() -> process(entry));
                } catch (RejectedExecutionException exception) {
                    claimService.returnUnstartedClaim(entry.getId(), claim.token())
                        .ifPresent(waiting -> store.requeue(waiting, claim.token()));
                }
            }, () -> returnUnclaimed(saleId, claim));
        } catch (RuntimeException exception) {
            log.error("Reservation queue dispatch failed for entry {}", claim.queueEntryId(), exception);
        }
    }

    private void returnUnclaimed(Long saleId, ReservationQueueClaim claim) {
        repository.findById(claim.queueEntryId()).filter(entry -> entry.getStatus() == ReservationQueueStatus.WAITING)
            .ifPresentOrElse(entry -> store.requeue(entry, claim.token()),
                () -> store.releaseProcessing(saleId, claim.queueEntryId(), claim.token()));
    }

    private void process(ReservationQueueEntry entry) {
        try {
            processingService.process(entry.getId(), entry.getProcessingToken()).ifPresent(store::releaseProcessing);
        } catch (RuntimeException exception) {
            log.error("Reservation queue processing failed for entry {}", entry.getId(), exception);
        }
    }
}
