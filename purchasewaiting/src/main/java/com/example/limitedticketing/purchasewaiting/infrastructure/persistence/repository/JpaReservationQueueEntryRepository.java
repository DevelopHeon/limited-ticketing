package com.example.limitedticketing.purchasewaiting.infrastructure.persistence.repository;

import com.example.limitedticketing.purchasewaiting.infrastructure.persistence.entity.ReservationQueueEntryJpaEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReservationQueueEntryRepository extends JpaRepository<ReservationQueueEntryJpaEntity, UUID> {

    Optional<ReservationQueueEntryJpaEntity> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    @Query("""
        select distinct entry.saleProductId
        from ReservationQueueEntryJpaEntity entry
        where entry.status = com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus.WAITING
        """)
    List<Long> findSaleProductIdsWithWaitingEntries();

    @Query(value = """
        SELECT * FROM reservation_queue_entries
        WHERE sale_product_id = :saleProductId AND status = 'WAITING'
          AND (queue_sequence, id) > (:sequence, CAST(:entryId AS uuid))
        ORDER BY queue_sequence, id LIMIT :limit
        """, nativeQuery = true)
    List<ReservationQueueEntryJpaEntity> findWaitingPage(
        @Param("saleProductId") Long saleProductId, @Param("sequence") long sequence,
        @Param("entryId") UUID entryId, @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM reservation_queue_entries
        WHERE status = 'PROCESSING' AND lease_until <= :now
        ORDER BY lease_until, id LIMIT :limit FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ReservationQueueEntryJpaEntity> findExpiredProcessingPage(
        @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query(value = "SELECT * FROM reservation_queue_entries WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ReservationQueueEntryJpaEntity> findLocked(@Param("id") UUID id);

    @Query(value = "SELECT * FROM reservation_queue_entries WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<ReservationQueueEntryJpaEntity> findLockedIfAvailable(@Param("id") UUID id);

    @Query(value = """
        SELECT CAST(pg_advisory_xact_lock(hashtextextended('reservation-queue:' || CAST(:saleId AS text), 0)) AS text)
        """, nativeQuery = true)
    String lockQueue(@Param("saleId") Long saleId);

    long countBySaleProductIdAndStatusIn(Long saleProductId,
        Collection<com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE reservation_queue_entries SET status = 'PROCESSING', processing_token = :token,
            processing_started_at = :now, lease_until = :leaseUntil
        WHERE id = :id AND status = 'WAITING'
        """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("token") UUID token,
        @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Query(value = """
        INSERT INTO reservation_queue_sequences (sale_product_id, last_sequence) VALUES (:saleId, 1)
        ON CONFLICT (sale_product_id) DO UPDATE
            SET last_sequence = reservation_queue_sequences.last_sequence + 1
        RETURNING last_sequence
        """, nativeQuery = true)
    long nextSequence(@Param("saleId") Long saleId);

    @Query("""
        select entry.id from ReservationQueueEntryJpaEntity entry where entry.id in :queueEntryIds
        and entry.status in (com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus.WAITING,
            com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus.PROCESSING)
        """)
    List<UUID> findActiveIds(@Param("queueEntryIds") Collection<UUID> queueEntryIds);

    boolean existsBySaleProductIdAndStatusIn(
        Long saleProductId,
        Collection<com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus> statuses
    );

    @Modifying
    @Query(value = """
        INSERT INTO reservation_queue_entries (
            id,
            member_id,
            sale_product_id,
            purchase_request_key,
            queue_sequence,
            status,
            created_at
        ) VALUES (
            :id,
            :memberId,
            :saleProductId,
            :purchaseRequestKey,
            :queueSequence,
            :status,
            :createdAt
        ) ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("memberId") Long memberId,
        @Param("saleProductId") Long saleProductId,
        @Param("purchaseRequestKey") String purchaseRequestKey,
        @Param("queueSequence") long queueSequence,
        @Param("status") String status,
        @Param("createdAt") LocalDateTime createdAt
    );
}
