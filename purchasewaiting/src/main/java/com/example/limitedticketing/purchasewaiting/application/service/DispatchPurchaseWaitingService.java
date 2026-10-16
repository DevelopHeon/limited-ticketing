package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryQueryPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingTaskExecutionPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingClaim;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.DispatchPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
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
public class DispatchPurchaseWaitingService implements DispatchPurchaseWaitingUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchPurchaseWaitingService.class);

    private final PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    private final ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase;
    private final ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase;
    private final PurchaseWaitingTaskExecutionPort purchaseWaitingTaskExecutionPort;
    private final Clock clock;
    private final int maxPerSale;
    private final int batchSize;
    private final AtomicInteger nextProduct = new AtomicInteger();

    public DispatchPurchaseWaitingService(
        PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort,
        ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase,
        ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase,
        PurchaseWaitingTaskExecutionPort purchaseWaitingTaskExecutionPort,
        Clock clock,
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxPerSale,
        @Value("${limited-ticketing.queue.worker.claim-batch-size}") int batchSize
    ) {
        this.purchaseWaitingEntryQueryPort = purchaseWaitingEntryQueryPort;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
        this.claimPurchaseWaitingUseCase = claimPurchaseWaitingUseCase;
        this.processPurchaseWaitingUseCase = processPurchaseWaitingUseCase;
        this.purchaseWaitingTaskExecutionPort = purchaseWaitingTaskExecutionPort;
        this.clock = clock;
        this.maxPerSale = maxPerSale;
        this.batchSize = batchSize;
    }

    @Override
    public void dispatchWaitingEntries() {
        int available = purchaseWaitingTaskExecutionPort.availableSlots();
        if (available <= 0) {
            return;
        }
        List<Long> sales = purchaseWaitingEntryQueryPort.findSaleProductIdsWithWaitingEntries().stream().sorted().toList();
        if (sales.isEmpty()) {
            return;
        }
        int first = Math.floorMod(nextProduct.getAndIncrement(), sales.size());
        for (int offset = 0; offset < sales.size() && available > 0; offset++) {
            Long saleId = sales.get((first + offset) % sales.size());
            List<PurchaseWaitingClaim> claims = purchaseWaitingQueuePort.claimNext(saleId, maxPerSale,
                Math.min(batchSize, available), Instant.now(clock));
            for (PurchaseWaitingClaim claim : claims) {
                dispatch(saleId, claim);
                available--;
            }
        }
    }

    private void dispatch(Long saleId, PurchaseWaitingClaim claim) {
        try {
            claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(claim.waitingEntryId(), claim.token())).ifPresentOrElse(entry -> {
                try {
                    purchaseWaitingTaskExecutionPort.execute(() -> process(entry));
                } catch (RejectedExecutionException exception) {
                    claimPurchaseWaitingUseCase.returnUnstartedClaim(new ClaimPurchaseWaitingCommand(entry.getId(), claim.token()))
                        .ifPresent(waiting -> purchaseWaitingQueuePort.requeue(waiting, claim.token()));
                }
            }, () -> returnUnclaimed(saleId, claim));
        } catch (RuntimeException exception) {
            log.error("Reservation queue dispatch failed for entry {}", claim.waitingEntryId(), exception);
        }
    }

    private void returnUnclaimed(Long saleId, PurchaseWaitingClaim claim) {
        purchaseWaitingEntryQueryPort.findById(claim.waitingEntryId()).filter(entry -> entry.getStatus() == PurchaseWaitingStatus.WAITING)
            .ifPresentOrElse(entry -> purchaseWaitingQueuePort.requeue(entry, claim.token()),
                () -> purchaseWaitingQueuePort.releaseProcessing(saleId, claim.waitingEntryId(), claim.token()));
    }

    private void process(PurchaseWaitingEntry entry) {
        try {
            processPurchaseWaitingUseCase.process(new ProcessPurchaseWaitingCommand(entry.getId(), entry.getProcessingToken())).ifPresent(purchaseWaitingQueuePort::releaseProcessing);
        } catch (RuntimeException exception) {
            log.error("Reservation queue processing failed for entry {}", entry.getId(), exception);
        }
    }
}
