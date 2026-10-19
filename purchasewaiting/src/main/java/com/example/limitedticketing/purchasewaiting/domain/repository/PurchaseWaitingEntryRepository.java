package com.example.limitedticketing.purchasewaiting.domain.repository;

import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PurchaseWaitingEntryRepository {

    long countActive(Long saleProductId);

    long countInFlight(Long saleProductId);

    long countInFlight();

    void lockAdmission();

    Optional<PurchaseWaitingEntry> findByIdForUpdate(UUID queueEntryId);

    Optional<PurchaseWaitingEntry> findByIdForUpdateSkipLocked(UUID queueEntryId);

    Optional<PurchaseWaitingEntry> findById(UUID queueEntryId);

    Optional<Long> findSaleProductId(UUID queueEntryId);

    Optional<PurchaseWaitingEntry> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    List<Long> findSaleProductIdsWithWaitingEntries();

    List<PurchaseWaitingEntry> findWaitingEntriesAfter(Long saleProductId, long queueSequence, UUID queueEntryId, int limit);

    List<PurchaseWaitingEntry> findExpiredProcessingEntries(LocalDateTime threshold, int limit);

    Set<UUID> findActiveIds(Collection<UUID> queueEntryIds);

    boolean existsActiveEntry(Long saleProductId);

    boolean existsOtherActiveEntry(Long memberId, Long saleProductId, UUID excludedEntryId);

    void lockQueue(Long saleProductId);

    Optional<PurchaseWaitingEntry> dispatch(UUID queueEntryId, UUID token, LocalDateTime now);

    PurchaseWaitingEntry save(PurchaseWaitingEntry entry);

    PurchaseWaitingInsertResult saveIfAbsent(PurchaseWaitingEntry entry);
}
