package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueRecoveryStore;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import com.example.limitedticketing.sale.application.port.in.SaleProductQueryFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import java.time.Clock;
import java.time.Instant;
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
    private final int recoveryBatchSize;

    public ReservationQueueRecoveryService(
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        ReservationQueueStore reservationQueueStore,
        ReservationQueueRecoveryStore reservationQueueRecoveryStore,
        SaleProductQueryFacade saleProductQueryFacade,
        Clock clock,
        @Value("${limited-ticketing.queue.worker.recovery-batch-size:1000}") int recoveryBatchSize
    ) {
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.reservationQueueStore = reservationQueueStore;
        this.reservationQueueRecoveryStore = reservationQueueRecoveryStore;
        this.saleProductQueryFacade = saleProductQueryFacade;
        this.clock = clock;
        if (recoveryBatchSize <= 0) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    @Transactional
    public void recover(long processingTimeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now(clock).minusSeconds(processingTimeoutSeconds);
        recoverExpiredProcessingEntries(threshold);
        registerRedisRecoveryAfterCommit(threshold.atZone(clock.getZone()).toInstant());
    }

    private void recoverExpiredProcessingEntries(LocalDateTime threshold) {
        while (true) {
            List<ReservationQueueEntry> entries = reservationQueueEntryRepository
                .findProcessingEntriesStartedBefore(threshold, recoveryBatchSize);
            if (entries.isEmpty()) {
                return;
            }

            entries.forEach(entry -> {
                entry.returnToWaiting();
                reservationQueueEntryRepository.save(entry);
            });
        }
    }

    private void registerRedisRecoveryAfterCommit(Instant threshold) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    synchronizeRedis(threshold);
                } catch (RuntimeException exception) {
                    log.error("Reservation queue recovery could not synchronize Redis", exception);
                }
            }
        });
    }

    private void synchronizeRedis(Instant threshold) {
        Set<Long> saleProductIds = findRecoverySaleProductIds();
        saleProductIds.forEach(
            saleProductId -> reservationQueueRecoveryStore.removeProcessingOlderThan(saleProductId, threshold)
        );

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
        while (true) {
            List<ReservationQueueEntry> entries = reservationQueueEntryRepository.findWaitingEntriesAfter(
                saleProductId,
                cursor,
                recoveryBatchSize
            );
            if (entries.isEmpty()) {
                return;
            }

            entries.forEach(entry -> {
                if (!reservationQueueStore.contains(entry.getSaleProductId(), entry.getId())) {
                    reservationQueueStore.enqueue(entry);
                }
            });
            cursor = entries.get(entries.size() - 1).getQueueSequence();
        }
    }

    private void removeOrphanedRedisEntries(Long saleProductId) {
        Set<UUID> redisEntryIds = new HashSet<>(reservationQueueRecoveryStore.findWaitingEntryIds(saleProductId));
        redisEntryIds.addAll(reservationQueueRecoveryStore.findProcessingEntryIds(saleProductId));
        if (redisEntryIds.isEmpty()) {
            return;
        }

        Set<UUID> orphanedEntryIds = new HashSet<>(redisEntryIds);
        orphanedEntryIds.removeAll(findExistingIds(redisEntryIds));
        reservationQueueRecoveryStore.removeWaiting(saleProductId, orphanedEntryIds);
        reservationQueueRecoveryStore.removeProcessing(saleProductId, orphanedEntryIds);
    }

    private Set<UUID> findExistingIds(Set<UUID> queueEntryIds) {
        List<UUID> ids = new ArrayList<>(queueEntryIds);
        Set<UUID> existingIds = new HashSet<>();
        for (int offset = 0; offset < ids.size(); offset += recoveryBatchSize) {
            existingIds.addAll(reservationQueueEntryRepository.findExistingIds(
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
}
