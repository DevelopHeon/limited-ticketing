package com.example.limitedticketing.purchasewaiting.infrastructure.persistence.entity;

import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservation_queue_entries")
public class ReservationQueueEntryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "sale_product_id", nullable = false, updatable = false)
    private Long saleProductId;

    @Column(name = "purchase_request_key", nullable = false, updatable = false, length = 100)
    private String purchaseRequestKey;

    @Column(name = "queue_sequence", nullable = false, updatable = false)
    private long queueSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationQueueStatus status;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    protected ReservationQueueEntryJpaEntity() {
    }

    private ReservationQueueEntryJpaEntity(ReservationQueueEntry entry) {
        this.id = entry.getId();
        this.memberId = entry.getMemberId();
        this.saleProductId = entry.getSaleProductId();
        this.purchaseRequestKey = entry.getPurchaseRequestKey();
        this.queueSequence = entry.getQueueSequence();
        this.status = entry.getStatus();
        this.purchaseId = entry.getPurchaseId();
        this.failureCode = entry.getFailureCode();
        this.createdAt = entry.getCreatedAt();
        this.processingStartedAt = entry.getProcessingStartedAt();
        this.completedAt = entry.getCompletedAt();
        this.processingToken = entry.getProcessingToken();
        this.leaseUntil = entry.getLeaseUntil();
    }

    public static ReservationQueueEntryJpaEntity from(ReservationQueueEntry entry) {
        return new ReservationQueueEntryJpaEntity(entry);
    }

    public ReservationQueueEntry toDomain() {
        return ReservationQueueEntry.reconstitute(
            id,
            memberId,
            saleProductId,
            purchaseRequestKey,
            queueSequence,
            status,
            purchaseId,
            failureCode,
            createdAt,
            processingStartedAt,
            completedAt,
            processingToken,
            leaseUntil
        );
    }
}
