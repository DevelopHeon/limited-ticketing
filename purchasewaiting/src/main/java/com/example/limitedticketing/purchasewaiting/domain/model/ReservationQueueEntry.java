package com.example.limitedticketing.purchasewaiting.domain.model;

import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReservationQueueEntry {

    private final UUID id;
    private final Long memberId;
    private final Long saleProductId;
    private final String purchaseRequestKey;
    private final long queueSequence;
    private ReservationQueueStatus status;
    private Long purchaseId;
    private String failureCode;
    private final LocalDateTime createdAt;
    private LocalDateTime processingStartedAt;
    private LocalDateTime completedAt;

    private ReservationQueueEntry(
        UUID id,
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        long queueSequence,
        ReservationQueueStatus status,
        Long purchaseId,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime processingStartedAt,
        LocalDateTime completedAt
    ) {
        if (id == null || memberId == null || saleProductId == null || purchaseRequestKey == null
            || purchaseRequestKey.isBlank() || queueSequence <= 0 || status == null || createdAt == null) {
            throw ReservationQueueException.invalid("Queue entry fields are required");
        }

        this.id = id;
        this.memberId = memberId;
        this.saleProductId = saleProductId;
        this.purchaseRequestKey = purchaseRequestKey;
        this.queueSequence = queueSequence;
        this.status = status;
        this.purchaseId = purchaseId;
        this.failureCode = failureCode;
        this.createdAt = createdAt;
        this.processingStartedAt = processingStartedAt;
        this.completedAt = completedAt;
    }

    public static ReservationQueueEntry enqueue(
        UUID id,
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        long queueSequence,
        LocalDateTime createdAt
    ) {
        return new ReservationQueueEntry(
            id,
            memberId,
            saleProductId,
            purchaseRequestKey,
            queueSequence,
            ReservationQueueStatus.WAITING,
            null,
            null,
            createdAt,
            null,
            null
        );
    }

    public static ReservationQueueEntry reconstitute(
        UUID id,
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        long queueSequence,
        ReservationQueueStatus status,
        Long purchaseId,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime processingStartedAt,
        LocalDateTime completedAt
    ) {
        return new ReservationQueueEntry(
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
            completedAt
        );
    }

    public void beginProcessing(LocalDateTime now) {
        if (status != ReservationQueueStatus.WAITING) {
            throw ReservationQueueException.invalidState("Only waiting queue entries can begin processing");
        }

        status = ReservationQueueStatus.PROCESSING;
        processingStartedAt = now;
    }

    public void completeReservation(Long purchaseId, LocalDateTime now) {
        if (status != ReservationQueueStatus.PROCESSING || purchaseId == null) {
            throw ReservationQueueException.invalidState("Only processing queue entries can be reserved");
        }

        status = ReservationQueueStatus.RESERVED;
        this.purchaseId = purchaseId;
        completedAt = now;
    }

    public void markSoldOut(String failureCode, LocalDateTime now) {
        complete(ReservationQueueStatus.SOLD_OUT, failureCode, now);
    }

    public void fail(String failureCode, LocalDateTime now) {
        complete(ReservationQueueStatus.FAILED, failureCode, now);
    }

    public void returnToWaiting() {
        if (status != ReservationQueueStatus.PROCESSING) {
            throw ReservationQueueException.invalidState("Only processing queue entries can return to waiting");
        }

        status = ReservationQueueStatus.WAITING;
        processingStartedAt = null;
    }

    private void complete(ReservationQueueStatus completedStatus, String failureCode, LocalDateTime now) {
        if (status != ReservationQueueStatus.PROCESSING) {
            throw ReservationQueueException.invalidState("Only processing queue entries can complete");
        }

        status = completedStatus;
        this.failureCode = failureCode;
        completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getSaleProductId() {
        return saleProductId;
    }

    public String getPurchaseRequestKey() {
        return purchaseRequestKey;
    }

    public long getQueueSequence() {
        return queueSequence;
    }

    public ReservationQueueStatus getStatus() {
        return status;
    }

    public Long getPurchaseId() {
        return purchaseId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}
