package com.example.limitedticketing.purchasewaiting.domain.model;

import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import java.time.LocalDateTime;
import java.util.UUID;

public class PurchaseWaitingEntry {

    private final UUID id;
    private final Long memberId;
    private final Long saleProductId;
    private final String purchaseRequestKey;
    private final long queueSequence;
    private PurchaseWaitingStatus status;
    private Long purchaseId;
    private String failureCode;
    private final LocalDateTime createdAt;
    private LocalDateTime processingStartedAt;
    private LocalDateTime completedAt;
    private UUID processingToken;
    private LocalDateTime leaseUntil;

    private PurchaseWaitingEntry(
        UUID id,
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        long queueSequence,
        PurchaseWaitingStatus status,
        Long purchaseId,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime processingStartedAt,
        LocalDateTime completedAt,
        UUID processingToken,
        LocalDateTime leaseUntil
    ) {
        if (id == null || memberId == null || saleProductId == null || purchaseRequestKey == null
            || purchaseRequestKey.isBlank() || queueSequence <= 0 || queueSequence > 9_007_199_254_740_991L
            || status == null || createdAt == null) {
            throw PurchaseWaitingException.invalid("Queue entry fields are required");
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
        this.processingToken = processingToken;
        this.leaseUntil = leaseUntil;
    }

    public static PurchaseWaitingEntry enqueue(
        UUID id,
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        long queueSequence,
        LocalDateTime createdAt
    ) {
        return new PurchaseWaitingEntry(
            id,
            memberId,
            saleProductId,
            purchaseRequestKey,
            queueSequence,
            PurchaseWaitingStatus.WAITING,
            null,
            null,
            createdAt,
            null,
            null,
            null,
            null
        );
    }

    public static PurchaseWaitingEntry reconstitute(
        UUID id,
        Long memberId,
        Long saleProductId,
        String purchaseRequestKey,
        long queueSequence,
        PurchaseWaitingStatus status,
        Long purchaseId,
        String failureCode,
        LocalDateTime createdAt,
        LocalDateTime processingStartedAt,
        LocalDateTime completedAt,
        UUID processingToken,
        LocalDateTime leaseUntil
    ) {
        return new PurchaseWaitingEntry(
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

    public void beginProcessing(UUID token, LocalDateTime now, LocalDateTime leaseUntil) {
        if (status != PurchaseWaitingStatus.WAITING || token == null || now == null
            || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw PurchaseWaitingException.invalidState("Only waiting queue entries can begin processing");
        }

        status = PurchaseWaitingStatus.PROCESSING;
        processingStartedAt = now;
        processingToken = token;
        this.leaseUntil = leaseUntil;
    }

    public void completeReservation(Long purchaseId, LocalDateTime now) {
        if (status != PurchaseWaitingStatus.PROCESSING || purchaseId == null) {
            throw PurchaseWaitingException.invalidState("Only processing queue entries can be reserved");
        }

        status = PurchaseWaitingStatus.RESERVED;
        this.purchaseId = purchaseId;
        completedAt = now;
    }

    public void markSoldOut(String failureCode, LocalDateTime now) {
        complete(PurchaseWaitingStatus.SOLD_OUT, failureCode, now);
    }

    public void fail(String failureCode, LocalDateTime now) {
        complete(PurchaseWaitingStatus.FAILED, failureCode, now);
    }

    public void returnToWaiting() {
        if (status != PurchaseWaitingStatus.PROCESSING) {
            throw PurchaseWaitingException.invalidState("Only processing queue entries can return to waiting");
        }

        status = PurchaseWaitingStatus.WAITING;
        processingStartedAt = null;
        processingToken = null;
        leaseUntil = null;
    }

    private void complete(PurchaseWaitingStatus completedStatus, String failureCode, LocalDateTime now) {
        if (status != PurchaseWaitingStatus.PROCESSING) {
            throw PurchaseWaitingException.invalidState("Only processing queue entries can complete");
        }

        status = completedStatus;
        this.failureCode = failureCode;
        completedAt = now;
    }

    public boolean ownsAttempt(UUID token) {
        return status == PurchaseWaitingStatus.PROCESSING && token != null && token.equals(processingToken);
    }

    public UUID getProcessingToken() {
        return processingToken;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
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

    public PurchaseWaitingStatus getStatus() {
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
