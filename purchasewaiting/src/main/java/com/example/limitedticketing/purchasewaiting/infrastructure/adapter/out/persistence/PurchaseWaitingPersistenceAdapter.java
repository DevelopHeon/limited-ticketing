package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryCommandPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryQueryPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingInsertResult;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.PurchaseWaitingJpaRepository;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.entity.PurchaseWaitingJpaEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseWaitingPersistenceAdapter implements PurchaseWaitingEntryQueryPort, PurchaseWaitingEntryCommandPort {

    private final PurchaseWaitingJpaRepository purchaseWaitingJpaRepository;

    public PurchaseWaitingPersistenceAdapter(PurchaseWaitingJpaRepository purchaseWaitingJpaRepository) {
        this.purchaseWaitingJpaRepository = purchaseWaitingJpaRepository;
    }

    @Override
    public void lockQueue(Long saleProductId) {
        purchaseWaitingJpaRepository.lockQueue(saleProductId);
    }

    @Override
    public long countActive(Long saleProductId) {
        return purchaseWaitingJpaRepository.countBySaleProductIdAndStatusIn(
            saleProductId, List.of(PurchaseWaitingStatus.WAITING, PurchaseWaitingStatus.PROCESSING));
    }

    @Override
    public long countProcessing(Long saleProductId) {
        return purchaseWaitingJpaRepository.countBySaleProductIdAndStatusIn(
            saleProductId, List.of(PurchaseWaitingStatus.PROCESSING));
    }

    @Override
    public Optional<PurchaseWaitingEntry> claim(UUID id, UUID token, LocalDateTime now, LocalDateTime leaseUntil) {
        return purchaseWaitingJpaRepository.claim(id, token, now, leaseUntil) == 1 ? findById(id) : Optional.empty();
    }

    @Override
    public Optional<PurchaseWaitingEntry> findByIdForUpdate(UUID id) {
        return purchaseWaitingJpaRepository.findLocked(id).map(PurchaseWaitingJpaEntity::toDomain);
    }

    @Override
    public Optional<PurchaseWaitingEntry> findByIdForUpdateSkipLocked(UUID id) {
        return purchaseWaitingJpaRepository.findLockedIfAvailable(id).map(PurchaseWaitingJpaEntity::toDomain);
    }

    @Override
    public PurchaseWaitingEntry save(PurchaseWaitingEntry entry) {
        return purchaseWaitingJpaRepository.save(PurchaseWaitingJpaEntity.from(entry)).toDomain();
    }

    @Override
    public PurchaseWaitingInsertResult saveIfAbsent(PurchaseWaitingEntry entry) {
        int inserted = purchaseWaitingJpaRepository.insertIfAbsent(
            entry.getId(),
            entry.getMemberId(),
            entry.getSaleProductId(),
            entry.getPurchaseRequestKey(),
            entry.getQueueSequence(),
            entry.getStatus().name(),
            entry.getCreatedAt()
        );

        Optional<PurchaseWaitingEntry> persisted = findByMemberIdAndPurchaseRequestKey(
            entry.getMemberId(),
            entry.getPurchaseRequestKey()
        );
        if (persisted.isPresent()) {
            return new PurchaseWaitingInsertResult(persisted.get(), inserted == 1);
        }
        if (inserted == 0) {
            throw PurchaseWaitingException.activeEntryExists();
        }

        throw PurchaseWaitingException.persistenceFailure();
    }

    @Override
    public Optional<PurchaseWaitingEntry> findById(UUID queueEntryId) {
        return purchaseWaitingJpaRepository.findById(queueEntryId).map(PurchaseWaitingJpaEntity::toDomain);
    }

    @Override
    public Optional<PurchaseWaitingEntry> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey) {
        return purchaseWaitingJpaRepository.findByMemberIdAndPurchaseRequestKey(memberId, purchaseRequestKey)
            .map(PurchaseWaitingJpaEntity::toDomain);
    }

    @Override
    public List<Long> findSaleProductIdsWithWaitingEntries() {
        return purchaseWaitingJpaRepository.findSaleProductIdsWithWaitingEntries();
    }

    @Override
    public List<PurchaseWaitingEntry> findWaitingEntriesAfter(Long saleProductId, long sequence, UUID entryId, int limit) {
        return purchaseWaitingJpaRepository.findWaitingPage(saleProductId, sequence, entryId, limit)
            .stream().map(PurchaseWaitingJpaEntity::toDomain).toList();
    }

    @Override
    public List<PurchaseWaitingEntry> findExpiredProcessingEntries(LocalDateTime now, int limit) {
        return purchaseWaitingJpaRepository.findExpiredProcessingPage(now, limit)
            .stream().map(PurchaseWaitingJpaEntity::toDomain).toList();
    }

    @Override
    public Set<UUID> findActiveIds(Collection<UUID> queueEntryIds) {
        if (queueEntryIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(purchaseWaitingJpaRepository.findActiveIds(queueEntryIds));
    }

    @Override
    public boolean existsActiveEntry(Long saleProductId) {
        return purchaseWaitingJpaRepository.existsBySaleProductIdAndStatusIn(
            saleProductId,
            List.of(PurchaseWaitingStatus.WAITING, PurchaseWaitingStatus.PROCESSING)
        );
    }
}
