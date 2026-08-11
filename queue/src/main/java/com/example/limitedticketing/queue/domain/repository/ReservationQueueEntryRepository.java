package com.example.limitedticketing.queue.domain.repository;

import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReservationQueueEntryRepository {

    ReservationQueueEntry save(ReservationQueueEntry entry);

    IdempotentReservationQueueEntry saveIfAbsent(ReservationQueueEntry entry);

    Optional<ReservationQueueEntry> findById(UUID queueEntryId);

    Optional<ReservationQueueEntry> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    List<Long> findSaleProductIdsWithWaitingEntries();

    List<ReservationQueueEntry> findWaitingEntriesAfter(Long saleProductId, long queueSequence, int limit);

    List<ReservationQueueEntry> findProcessingEntriesStartedBefore(LocalDateTime threshold, int limit);

    Set<UUID> findExistingIds(Collection<UUID> queueEntryIds);

    boolean existsActiveEntry(Long saleProductId);
}
