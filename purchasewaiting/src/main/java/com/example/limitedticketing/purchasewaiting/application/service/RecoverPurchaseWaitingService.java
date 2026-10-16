package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryCommandPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryQueryPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueueRecoveryPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.SaleProductQueryPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecoverPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RestorePurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RecoverPurchaseWaitingService implements RecoverPurchaseWaitingUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecoverPurchaseWaitingService.class);

    private final PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort;
    private final PurchaseWaitingEntryCommandPort purchaseWaitingEntryCommandPort;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    private final PurchaseWaitingQueueRecoveryPort purchaseWaitingQueueRecoveryPort;
    private final SaleProductQueryPort saleProductQueryPort;
    private final Clock clock;
    private final RestorePurchaseWaitingUseCase restorePurchaseWaitingUseCase;
    private final int recoveryBatchSize;

    public RecoverPurchaseWaitingService(
        PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort,
        PurchaseWaitingEntryCommandPort purchaseWaitingEntryCommandPort,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort,
        PurchaseWaitingQueueRecoveryPort purchaseWaitingQueueRecoveryPort,
        SaleProductQueryPort saleProductQueryPort,
        Clock clock,
        RestorePurchaseWaitingUseCase restorePurchaseWaitingUseCase,
        @Value("${limited-ticketing.queue.worker.recovery-batch-size:1000}") int recoveryBatchSize
    ) {
        this.purchaseWaitingEntryQueryPort = purchaseWaitingEntryQueryPort;
        this.purchaseWaitingEntryCommandPort = purchaseWaitingEntryCommandPort;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
        this.purchaseWaitingQueueRecoveryPort = purchaseWaitingQueueRecoveryPort;
        this.saleProductQueryPort = saleProductQueryPort;
        this.clock = clock;
        this.restorePurchaseWaitingUseCase = restorePurchaseWaitingUseCase;
        if (recoveryBatchSize <= 0) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Override
    @Transactional
    public void recover() {
        List<RecoveredAttempt> recovered = recoverExpiredProcessingEntries(LocalDateTime.now(clock));
        registerRedisRecoveryAfterCommit(recovered);
    }

    private List<RecoveredAttempt> recoverExpiredProcessingEntries(LocalDateTime threshold) {
        List<RecoveredAttempt> recovered = new ArrayList<>();
        while (true) {
            List<PurchaseWaitingEntry> entries = purchaseWaitingEntryQueryPort
                .findExpiredProcessingEntries(threshold, recoveryBatchSize);
            if (entries.isEmpty()) {
                return recovered;
            }

            entries.forEach(entry -> {
                UUID token = entry.getProcessingToken();
                entry.returnToWaiting();
                recovered.add(new RecoveredAttempt(entry, token));
                purchaseWaitingEntryCommandPort.save(entry);
            });
        }
    }

    private void registerRedisRecoveryAfterCommit(List<RecoveredAttempt> recovered) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    recovered.forEach(attempt -> purchaseWaitingQueuePort.requeue(attempt.entry(), attempt.token()));
                    synchronizeRedis();
                } catch (RuntimeException exception) {
                    log.error("Reservation queue recovery could not synchronize Redis", exception);
                }
            }
        });
    }

    private void synchronizeRedis() {
        Set<Long> saleProductIds = findRecoverySaleProductIds();
        recoverAllWaitingEntries();
        saleProductIds.addAll(findRecoverySaleProductIds());
        saleProductIds.forEach(this::removeOrphanedRedisEntries);
        saleProductIds.forEach(this::deleteCompletedClosedSaleKeysSafely);
    }

    private Set<Long> findRecoverySaleProductIds() {
        Set<Long> saleProductIds = new HashSet<>(purchaseWaitingEntryQueryPort.findSaleProductIdsWithWaitingEntries());
        saleProductIds.addAll(purchaseWaitingQueueRecoveryPort.findSaleProductIds());
        return saleProductIds;
    }

    private void recoverAllWaitingEntries() {
        purchaseWaitingEntryQueryPort.findSaleProductIdsWithWaitingEntries()
            .forEach(this::recoverWaitingEntries);
    }

    private void recoverWaitingEntries(Long saleProductId) {
        long cursor = 0;
        UUID entryCursor = new UUID(0, 0);
        while (true) {
            List<PurchaseWaitingEntry> entries = purchaseWaitingEntryQueryPort.findWaitingEntriesAfter(
                saleProductId,
                cursor,
                entryCursor,
                recoveryBatchSize
            );
            if (entries.isEmpty()) {
                return;
            }

            entries.forEach(entry -> {
                if (purchaseWaitingQueuePort.findPosition(entry.getSaleProductId(), entry.getId()).isEmpty()) {
                    restorePurchaseWaitingUseCase.restoreWaiting(entry.getId());
                }
            });
            cursor = entries.get(entries.size() - 1).getQueueSequence();
            entryCursor = entries.get(entries.size() - 1).getId();
        }
    }

    private void removeOrphanedRedisEntries(Long saleProductId) {
        Set<UUID> redisEntryIds = new HashSet<>(purchaseWaitingQueueRecoveryPort.findWaitingEntryIds(saleProductId));
        redisEntryIds.addAll(purchaseWaitingQueueRecoveryPort.findProcessingEntryIds(saleProductId));
        if (redisEntryIds.isEmpty()) {
            return;
        }

        Set<UUID> orphanedEntryIds = new HashSet<>(redisEntryIds);
        orphanedEntryIds.removeAll(findActiveIds(redisEntryIds));
        purchaseWaitingQueueRecoveryPort.removeWaiting(saleProductId, orphanedEntryIds);
        purchaseWaitingQueueRecoveryPort.removeProcessing(saleProductId, orphanedEntryIds);
    }

    private Set<UUID> findActiveIds(Set<UUID> queueEntryIds) {
        List<UUID> ids = new ArrayList<>(queueEntryIds);
        Set<UUID> existingIds = new HashSet<>();
        for (int offset = 0; offset < ids.size(); offset += recoveryBatchSize) {
            existingIds.addAll(purchaseWaitingEntryQueryPort.findActiveIds(
                ids.subList(offset, Math.min(offset + recoveryBatchSize, ids.size()))
            ));
        }
        return existingIds;
    }

    private void deleteCompletedClosedSaleKeys(Long saleProductId) {
        if (purchaseWaitingEntryQueryPort.existsActiveEntry(saleProductId)) {
            return;
        }
        if (!saleProductQueryPort.exists(saleProductId) || saleProductQueryPort.isClosed(saleProductId)) {
            purchaseWaitingQueueRecoveryPort.delete(saleProductId);
        }
    }

    private void deleteCompletedClosedSaleKeysSafely(Long saleProductId) {
        try {
            deleteCompletedClosedSaleKeys(saleProductId);
        } catch (RuntimeException exception) {
            log.warn("Could not determine whether Redis keys for sale product {} can be deleted", saleProductId, exception);
        }
    }
    private record RecoveredAttempt(PurchaseWaitingEntry entry, UUID token) {
    }
}
