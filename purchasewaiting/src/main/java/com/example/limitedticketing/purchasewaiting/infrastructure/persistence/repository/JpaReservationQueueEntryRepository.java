package com.example.limitedticketing.purchasewaiting.infrastructure.persistence.repository;

import com.example.limitedticketing.purchasewaiting.infrastructure.persistence.entity.ReservationQueueEntryJpaEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    List<ReservationQueueEntryJpaEntity> findBySaleProductIdAndStatusAndQueueSequenceGreaterThanOrderByQueueSequenceAsc(
        Long saleProductId,
        com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus status,
        long queueSequence,
        Pageable pageable
    );

    List<ReservationQueueEntryJpaEntity> findByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
        com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus status,
        LocalDateTime threshold,
        Pageable pageable
    );

    @Query("select entry.id from ReservationQueueEntryJpaEntity entry where entry.id in :queueEntryIds")
    List<UUID> findExistingIds(@Param("queueEntryIds") Collection<UUID> queueEntryIds);

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
