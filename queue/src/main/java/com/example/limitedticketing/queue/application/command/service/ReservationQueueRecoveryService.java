package com.example.limitedticketing.queue.application.command.service;

import com.example.limitedticketing.queue.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.queue.domain.repository.ReservationQueueEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReservationQueueRecoveryService {

    private static final int RECOVERY_BATCH_SIZE = 1_000;
    private static final Logger log = LoggerFactory.getLogger(ReservationQueueRecoveryService.class);

    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final ReservationQueueStore reservationQueueStore;
    private final Clock clock;

    public ReservationQueueRecoveryService(
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        ReservationQueueStore reservationQueueStore,
        Clock clock
    ) {
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.reservationQueueStore = reservationQueueStore;
        this.clock = clock;
    }

    @Transactional
    public void recover(long processingTimeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now(clock).minusSeconds(processingTimeoutSeconds);

        reservationQueueEntryRepository.findProcessingEntriesStartedBefore(threshold, RECOVERY_BATCH_SIZE)
            .forEach(entry -> {
                entry.returnToWaiting();
                reservationQueueEntryRepository.save(entry);
            });

        List<ReservationQueueEntry> waitingEntries = reservationQueueEntryRepository.findWaitingEntries(RECOVERY_BATCH_SIZE);
        registerRedisRecoveryAfterCommit(waitingEntries, threshold.atZone(clock.getZone()).toInstant());
    }

    private void registerRedisRecoveryAfterCommit(List<ReservationQueueEntry> waitingEntries, Instant threshold) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    waitingEntries.stream()
                        .map(ReservationQueueEntry::getSaleProductId)
                        .distinct()
                        .forEach(saleProductId -> reservationQueueStore.removeProcessingOlderThan(saleProductId, threshold));

                    waitingEntries.forEach(entry -> {
                        if (!reservationQueueStore.contains(entry.getSaleProductId(), entry.getId())) {
                            reservationQueueStore.enqueue(entry);
                        }
                    });
                } catch (RuntimeException exception) {
                    log.error("Reservation queue recovery could not synchronize Redis", exception);
                }
            }
        });
    }
}
