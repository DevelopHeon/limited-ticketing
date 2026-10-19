package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingClaim;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.DispatchPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DispatchPurchaseWaitingService implements DispatchPurchaseWaitingUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchPurchaseWaitingService.class);

    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    private final ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase;
    private final Clock clock;
    private final int globalConcurrentProcessing;
    private final int maxPerSale;
    private final int batchSize;
    private final AtomicInteger nextProduct = new AtomicInteger();

    public DispatchPurchaseWaitingService(
        PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort,
        ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase,
        Clock clock,
        @Value("${limited-ticketing.queue.worker.global-concurrent-processing:10}") int globalConcurrentProcessing,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxPerSale,
        @Value("${limited-ticketing.queue.worker.claim-batch-size}") int batchSize
    ) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
        this.claimPurchaseWaitingUseCase = claimPurchaseWaitingUseCase;
        this.clock = clock;
        this.globalConcurrentProcessing = globalConcurrentProcessing;
        this.maxPerSale = maxPerSale;
        this.batchSize = batchSize;
    }

    @Override
    public void dispatchWaitingEntries() {
        long available = globalConcurrentProcessing - purchaseWaitingEntryRepository.countInFlight();
        if (available <= 0) {
            return;
        }
        List<Long> sales = purchaseWaitingEntryRepository.findSaleProductIdsWithWaitingEntries().stream().sorted().toList();
        if (sales.isEmpty()) {
            return;
        }
        int first = Math.floorMod(nextProduct.getAndIncrement(), sales.size());
        for (int offset = 0; offset < sales.size() && available > 0; offset++) {
            Long saleId = sales.get((first + offset) % sales.size());
            List<PurchaseWaitingClaim> claims = purchaseWaitingQueuePort.claimNext(saleId, maxPerSale,
                (int) Math.min(batchSize, available), Instant.now(clock));
            for (PurchaseWaitingClaim claim : claims) {
                dispatch(saleId, claim);
                available--;
            }
        }
    }

    private void dispatch(Long saleId, PurchaseWaitingClaim claim) {
        try {
            if (claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(claim.waitingEntryId(), claim.token())).isEmpty()) {
                returnUnclaimed(saleId, claim);
            }
        } catch (RuntimeException exception) {
            log.error("Reservation queue dispatch failed for entry {}", claim.waitingEntryId(), exception);
        }
    }

    private void returnUnclaimed(Long saleId, PurchaseWaitingClaim claim) {
        purchaseWaitingEntryRepository.findById(claim.waitingEntryId()).filter(entry -> entry.getStatus() == PurchaseWaitingStatus.WAITING)
            .ifPresentOrElse(entry -> purchaseWaitingQueuePort.requeue(entry, claim.token()),
                () -> purchaseWaitingQueuePort.releaseProcessing(saleId, claim.waitingEntryId(), claim.token()));
    }

}
