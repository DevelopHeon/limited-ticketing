package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueRecoveryStore;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import com.example.limitedticketing.sale.application.port.in.SaleProductQueryFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
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
public class ReservationQueueRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationQueueRecoveryService.class);

    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final ReservationQueueStore reservationQueueStore;
    private final ReservationQueueRecoveryStore reservationQueueRecoveryStore;
    private final SaleProductQueryFacade saleProductQueryFacade;
    private final Clock clock;
    private final ReservationQueueReconciliationService reconciliationService;
    private final int recoveryBatchSize;

    public ReservationQueueRecoveryService(
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        ReservationQueueStore reservationQueueStore,
        ReservationQueueRecoveryStore reservationQueueRecoveryStore,
        SaleProductQueryFacade saleProductQueryFacade,
        Clock clock,
        ReservationQueueReconciliationService reconciliationService,
        @Value("${limited-ticketing.queue.worker.recovery-batch-size:1000}") int recoveryBatchSize
    ) {
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.reservationQueueStore = reservationQueueStore;
        this.reservationQueueRecoveryStore = reservationQueueRecoveryStore;
        this.saleProductQueryFacade = saleProductQueryFacade;
        this.clock = clock;
        this.reconciliationService = reconciliationService;
        if (recoveryBatchSize <= 0) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Transactional
    public void recover() {
        List<RecoveredAttempt> recovered = recoverExpiredProcessingEntries(LocalDateTime.now(clock));
        registerRedisRecoveryAfterCommit(recovered);
    }

    private List<RecoveredAttempt> recoverExpiredProcessingEntries(LocalDateTime threshold) {
        List<RecoveredAttempt> recovered = new ArrayList<>();
        while (true) {
            List<ReservationQueueEntry> entries = reservationQueueEntryRepository
                .findExpiredProcessingEntries(threshold, recoveryBatchSize);
            if (entries.isEmpty()) {
                return recovered;
            }

            entries.forEach(entry -> {
                UUID token = entry.getProcessingToken();
                entry.returnToWaiting();
                recovered.add(new RecoveredAttempt(entry, token));
                reservationQueueEntryRepository.save(entry);
            });
        }
    }

    private void registerRedisRecoveryAfterCommit(List<RecoveredAttempt> recovered) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    recovered.forEach(attempt -> reservationQueueStore.requeue(attempt.entry(), attempt.token()));
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
        Set<Long> saleProductIds = new HashSet<>(reservationQueueEntryRepository.findSaleProductIdsWithWaitingEntries());
        saleProductIds.addAll(reservationQueueRecoveryStore.findSaleProductIds());
        return saleProductIds;
    }

    private void recoverAllWaitingEntries() {
        reservationQueueEntryRepository.findSaleProductIdsWithWaitingEntries()
            .forEach(this::recoverWaitingEntries);
    }

    private void recoverWaitingEntries(Long saleProductId) {
        long cursor = 0;
        UUID entryCursor = new UUID(0, 0);
        while (true) {
            List<ReservationQueueEntry> entries = reservationQueueEntryRepository.findWaitingEntriesAfter(
                saleProductId,
                cursor,
                entryCursor,
                recoveryBatchSize
            );
            if (entries.isEmpty()) {
                return;
            }

            entries.forEach(entry -> {
                if (reservationQueueStore.findPosition(entry.getSaleProductId(), entry.getId()).isEmpty()) {
                    reconciliationService.restoreWaiting(entry.getId());
                }
            });
            cursor = entries.get(entries.size() - 1).getQueueSequence();
            entryCursor = entries.get(entries.size() - 1).getId();
        }
    }

    private void removeOrphanedRedisEntries(Long saleProductId) {
        Set<UUID> redisEntryIds = new HashSet<>(reservationQueueRecoveryStore.findWaitingEntryIds(saleProductId));
        redisEntryIds.addAll(reservationQueueRecoveryStore.findProcessingEntryIds(saleProductId));
        if (redisEntryIds.isEmpty()) {
            return;
        }

        Set<UUID> orphanedEntryIds = new HashSet<>(redisEntryIds);
        orphanedEntryIds.removeAll(findActiveIds(redisEntryIds));
        reservationQueueRecoveryStore.removeWaiting(saleProductId, orphanedEntryIds);
        reservationQueueRecoveryStore.removeProcessing(saleProductId, orphanedEntryIds);
    }

    private Set<UUID> findActiveIds(Set<UUID> queueEntryIds) {
        List<UUID> ids = new ArrayList<>(queueEntryIds);
        Set<UUID> existingIds = new HashSet<>();
        for (int offset = 0; offset < ids.size(); offset += recoveryBatchSize) {
            existingIds.addAll(reservationQueueEntryRepository.findActiveIds(
                ids.subList(offset, Math.min(offset + recoveryBatchSize, ids.size()))
            ));
        }
        return existingIds;
    }

    private void deleteCompletedClosedSaleKeys(Long saleProductId) {
        if (reservationQueueEntryRepository.existsActiveEntry(saleProductId)) {
            return;
        }
        FindSaleProductInput input = new FindSaleProductInput(saleProductId);
        if (!saleProductQueryFacade.exists(input) || saleProductQueryFacade.isClosed(input)) {
            reservationQueueRecoveryStore.delete(saleProductId);
        }
    }

    private void deleteCompletedClosedSaleKeysSafely(Long saleProductId) {
        try {
            deleteCompletedClosedSaleKeys(saleProductId);
        } catch (RuntimeException exception) {
            log.warn("Could not determine whether Redis keys for sale product {} can be deleted", saleProductId, exception);
        }
    }
    private record RecoveredAttempt(ReservationQueueEntry entry, UUID token) {
    }
}
