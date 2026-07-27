package com.example.limitedticketing.queue.infrastructure.persistence.repository;

import com.example.limitedticketing.queue.infrastructure.persistence.entity.ReservationQueueEntryJpaEntity;
import java.time.LocalDateTime;
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
        where entry.status = com.example.limitedticketing.queue.domain.model.ReservationQueueStatus.WAITING
        """)
    List<Long> findSaleProductIdsWithWaitingEntries();

    List<ReservationQueueEntryJpaEntity> findByStatusOrderByQueueSequenceAsc(
        com.example.limitedticketing.queue.domain.model.ReservationQueueStatus status,
        Pageable pageable
    );

    List<ReservationQueueEntryJpaEntity> findByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
        com.example.limitedticketing.queue.domain.model.ReservationQueueStatus status,
        LocalDateTime threshold,
        Pageable pageable
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
