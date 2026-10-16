package com.example.limitedticketing.purchasewaiting.domain.repository;

import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ReservationQueueEntryRepository {

    void lockQueue(Long saleProductId);

    long countActive(Long saleProductId);

    long countProcessing(Long saleProductId);

    Optional<ReservationQueueEntry> claim(UUID queueEntryId, UUID token, LocalDateTime now, LocalDateTime leaseUntil);

    Optional<ReservationQueueEntry> findByIdForUpdate(UUID queueEntryId);

    Optional<ReservationQueueEntry> findByIdForUpdateSkipLocked(UUID queueEntryId);

    ReservationQueueEntry save(ReservationQueueEntry entry);

    IdempotentReservationQueueEntry saveIfAbsent(ReservationQueueEntry entry);

    Optional<ReservationQueueEntry> findById(UUID queueEntryId);

    Optional<ReservationQueueEntry> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    List<Long> findSaleProductIdsWithWaitingEntries();

    List<ReservationQueueEntry> findWaitingEntriesAfter(Long saleProductId, long queueSequence, UUID queueEntryId, int limit);

    List<ReservationQueueEntry> findExpiredProcessingEntries(LocalDateTime threshold, int limit);

    Set<UUID> findActiveIds(Collection<UUID> queueEntryIds);

    boolean existsActiveEntry(Long saleProductId);
}
