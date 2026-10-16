package com.example.limitedticketing.purchasewaiting.infrastructure.persistence;

import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.IdempotentReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import com.example.limitedticketing.purchasewaiting.infrastructure.persistence.entity.ReservationQueueEntryJpaEntity;
import com.example.limitedticketing.purchasewaiting.infrastructure.persistence.repository.JpaReservationQueueEntryRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationQueueEntryRepositoryAdapter implements ReservationQueueEntryRepository {

    private final JpaReservationQueueEntryRepository jpaReservationQueueEntryRepository;

    public ReservationQueueEntryRepositoryAdapter(JpaReservationQueueEntryRepository jpaReservationQueueEntryRepository) {
        this.jpaReservationQueueEntryRepository = jpaReservationQueueEntryRepository;
    }

    @Override
    public void lockQueue(Long saleProductId) {
        jpaReservationQueueEntryRepository.lockQueue(saleProductId);
    }

    @Override
    public long countActive(Long saleProductId) {
        return jpaReservationQueueEntryRepository.countBySaleProductIdAndStatusIn(
            saleProductId, List.of(ReservationQueueStatus.WAITING, ReservationQueueStatus.PROCESSING));
    }

    @Override
    public long countProcessing(Long saleProductId) {
        return jpaReservationQueueEntryRepository.countBySaleProductIdAndStatusIn(
            saleProductId, List.of(ReservationQueueStatus.PROCESSING));
    }

    @Override
    public Optional<ReservationQueueEntry> claim(UUID id, UUID token, LocalDateTime now, LocalDateTime leaseUntil) {
        return jpaReservationQueueEntryRepository.claim(id, token, now, leaseUntil) == 1 ? findById(id) : Optional.empty();
    }

    @Override
    public Optional<ReservationQueueEntry> findByIdForUpdate(UUID id) {
        return jpaReservationQueueEntryRepository.findLocked(id).map(ReservationQueueEntryJpaEntity::toDomain);
    }

    @Override
    public Optional<ReservationQueueEntry> findByIdForUpdateSkipLocked(UUID id) {
        return jpaReservationQueueEntryRepository.findLockedIfAvailable(id).map(ReservationQueueEntryJpaEntity::toDomain);
    }

    @Override
    public ReservationQueueEntry save(ReservationQueueEntry entry) {
        return jpaReservationQueueEntryRepository.save(ReservationQueueEntryJpaEntity.from(entry)).toDomain();
    }

    @Override
    public IdempotentReservationQueueEntry saveIfAbsent(ReservationQueueEntry entry) {
        int inserted = jpaReservationQueueEntryRepository.insertIfAbsent(
            entry.getId(),
            entry.getMemberId(),
            entry.getSaleProductId(),
            entry.getPurchaseRequestKey(),
            entry.getQueueSequence(),
            entry.getStatus().name(),
            entry.getCreatedAt()
        );

        Optional<ReservationQueueEntry> persisted = findByMemberIdAndPurchaseRequestKey(
            entry.getMemberId(),
            entry.getPurchaseRequestKey()
        );
        if (persisted.isPresent()) {
            return new IdempotentReservationQueueEntry(persisted.get(), inserted == 1);
        }
        if (inserted == 0) {
            throw ReservationQueueException.activeEntryExists();
        }

        throw ReservationQueueException.persistenceFailure();
    }

    @Override
    public Optional<ReservationQueueEntry> findById(UUID queueEntryId) {
        return jpaReservationQueueEntryRepository.findById(queueEntryId).map(ReservationQueueEntryJpaEntity::toDomain);
    }

    @Override
    public Optional<ReservationQueueEntry> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey) {
        return jpaReservationQueueEntryRepository.findByMemberIdAndPurchaseRequestKey(memberId, purchaseRequestKey)
            .map(ReservationQueueEntryJpaEntity::toDomain);
    }

    @Override
    public List<Long> findSaleProductIdsWithWaitingEntries() {
        return jpaReservationQueueEntryRepository.findSaleProductIdsWithWaitingEntries();
    }

    @Override
    public List<ReservationQueueEntry> findWaitingEntriesAfter(Long saleProductId, long sequence, UUID entryId, int limit) {
        return jpaReservationQueueEntryRepository.findWaitingPage(saleProductId, sequence, entryId, limit)
            .stream().map(ReservationQueueEntryJpaEntity::toDomain).toList();
    }

    @Override
    public List<ReservationQueueEntry> findExpiredProcessingEntries(LocalDateTime now, int limit) {
        return jpaReservationQueueEntryRepository.findExpiredProcessingPage(now, limit)
            .stream().map(ReservationQueueEntryJpaEntity::toDomain).toList();
    }

    @Override
    public Set<UUID> findActiveIds(Collection<UUID> queueEntryIds) {
        if (queueEntryIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jpaReservationQueueEntryRepository.findActiveIds(queueEntryIds));
    }

    @Override
    public boolean existsActiveEntry(Long saleProductId) {
        return jpaReservationQueueEntryRepository.existsBySaleProductIdAndStatusIn(
            saleProductId,
            List.of(ReservationQueueStatus.WAITING, ReservationQueueStatus.PROCESSING)
        );
    }
}
