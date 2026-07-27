package com.example.limitedticketing.queue.domain.repository;

import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationQueueEntryRepository {

    ReservationQueueEntry save(ReservationQueueEntry entry);

    IdempotentReservationQueueEntry saveIfAbsent(ReservationQueueEntry entry);

    Optional<ReservationQueueEntry> findById(UUID queueEntryId);

    Optional<ReservationQueueEntry> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    List<Long> findSaleProductIdsWithWaitingEntries();

    List<ReservationQueueEntry> findWaitingEntries(int limit);

    List<ReservationQueueEntry> findProcessingEntriesStartedBefore(LocalDateTime threshold, int limit);
}
