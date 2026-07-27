package com.example.limitedticketing.queue.infrastructure.persistence;

import com.example.limitedticketing.queue.domain.exception.ReservationQueueException;
import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.queue.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.queue.domain.repository.IdempotentReservationQueueEntry;
import com.example.limitedticketing.queue.domain.repository.ReservationQueueEntryRepository;
import com.example.limitedticketing.queue.infrastructure.persistence.entity.ReservationQueueEntryJpaEntity;
import com.example.limitedticketing.queue.infrastructure.persistence.repository.JpaReservationQueueEntryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationQueueEntryRepositoryAdapter implements ReservationQueueEntryRepository {

    private final JpaReservationQueueEntryRepository jpaReservationQueueEntryRepository;

    public ReservationQueueEntryRepositoryAdapter(JpaReservationQueueEntryRepository jpaReservationQueueEntryRepository) {
        this.jpaReservationQueueEntryRepository = jpaReservationQueueEntryRepository;
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
    public List<ReservationQueueEntry> findWaitingEntries(int limit) {
        return jpaReservationQueueEntryRepository.findByStatusOrderByQueueSequenceAsc(
                ReservationQueueStatus.WAITING,
                PageRequest.of(0, limit)
            )
            .stream()
            .map(ReservationQueueEntryJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<ReservationQueueEntry> findProcessingEntriesStartedBefore(LocalDateTime threshold, int limit) {
        return jpaReservationQueueEntryRepository.findByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
                ReservationQueueStatus.PROCESSING,
                threshold,
                PageRequest.of(0, limit)
            )
            .stream()
            .map(ReservationQueueEntryJpaEntity::toDomain)
            .toList();
    }
}
