package com.example.limitedticketing.purchasewaiting.domain.model;

import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import java.time.LocalDateTime;
import java.util.UUID;

public class PurchaseWaitingEntry {

    public static final int MAX_EXECUTION_ATTEMPTS = 3;
    public static final String TECHNICAL_FAILURE_CODE = "RESERVATION_PROCESSING_FAILED";

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
    private LocalDateTime dispatchedAt;
    private int executionAttempts;
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
        dispatchedAt = processingStartedAt;
        executionAttempts = status == PurchaseWaitingStatus.PROCESSING ? 1 : 0;
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

    public static PurchaseWaitingEntry reconstitute(
        UUID id, Long memberId, Long saleProductId, String purchaseRequestKey, long queueSequence,
        PurchaseWaitingStatus status, Long purchaseId, String failureCode, LocalDateTime createdAt,
        LocalDateTime processingStartedAt, LocalDateTime completedAt, UUID processingToken,
        LocalDateTime leaseUntil, LocalDateTime dispatchedAt, int executionAttempts
    ) {
        if (executionAttempts < 0 || executionAttempts > MAX_EXECUTION_ATTEMPTS) {
            throw PurchaseWaitingException.invalid("Execution attempts are outside the allowed budget");
        }
        PurchaseWaitingEntry entry = reconstitute(id, memberId, saleProductId, purchaseRequestKey,
            queueSequence, status, purchaseId, failureCode, createdAt, processingStartedAt,
            completedAt, processingToken, leaseUntil);
        entry.dispatchedAt = dispatchedAt;
        entry.executionAttempts = executionAttempts;
        return entry;
    }

    public void dispatch(UUID token, LocalDateTime now) {
        if (status != PurchaseWaitingStatus.WAITING || token == null || now == null) {
            throw PurchaseWaitingException.invalidState("Only waiting entries can be dispatched");
        }
        status = PurchaseWaitingStatus.DISPATCHED;
        processingToken = token;
        dispatchedAt = now;
        leaseUntil = null;
    }

    public void beginProcessing(UUID token, LocalDateTime now, LocalDateTime leaseUntil) {
        if (status != PurchaseWaitingStatus.DISPATCHED || !ownsDispatch(token)
            || executionAttempts >= MAX_EXECUTION_ATTEMPTS || now == null
            || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw PurchaseWaitingException.invalidState("Only the current dispatched attempt can start within its budget");
        }
        status = PurchaseWaitingStatus.PROCESSING;
        processingStartedAt = now;
        this.leaseUntil = leaseUntil;
        executionAttempts++;
    }

    public void retryDispatched() {
        if (status != PurchaseWaitingStatus.PROCESSING) {
            throw PurchaseWaitingException.invalidState("Only processing entries can retry");
        }
        status = PurchaseWaitingStatus.DISPATCHED;
        leaseUntil = null;
    }

    public boolean ownsDispatch(UUID token) {
        return (status == PurchaseWaitingStatus.DISPATCHED || status == PurchaseWaitingStatus.PROCESSING)
            && token != null && token.equals(processingToken);
    }

    public void failExecution(LocalDateTime now) {
        if (status != PurchaseWaitingStatus.DISPATCHED && status != PurchaseWaitingStatus.PROCESSING) {
            throw PurchaseWaitingException.invalidState("Only an active delivery can fail execution");
        }
        status = PurchaseWaitingStatus.FAILED;
        failureCode = TECHNICAL_FAILURE_CODE;
        completedAt = now;
    }

    public void replay(LocalDateTime now) {
        boolean exhausted = status == PurchaseWaitingStatus.FAILED && TECHNICAL_FAILURE_CODE.equals(failureCode);
        boolean delayed = status == PurchaseWaitingStatus.DISPATCHED && dispatchedAt != null
            && !dispatchedAt.plusSeconds(60).isAfter(now);
        if (!exhausted && !delayed) {
            throw PurchaseWaitingException.invalidState("Only technical failures or delayed deliveries can be replayed");
        }
        if (exhausted) {
            executionAttempts = 0;
        }
        resetWaiting();
        completedAt = null;
        failureCode = null;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public int getExecutionAttempts() {
        return executionAttempts;
    }

    public void completeReservation(Long purchaseId, LocalDateTime now) {
        if ((status != PurchaseWaitingStatus.PROCESSING && status != PurchaseWaitingStatus.DISPATCHED) || purchaseId == null) {
            throw PurchaseWaitingException.invalidState("Only processing queue entries can be reserved");
        }

        status = PurchaseWaitingStatus.RESERVED;
        this.purchaseId = purchaseId;
        completedAt = now;
    }

    public void restoreReservation(Long purchaseId, LocalDateTime now) {
        if (status == PurchaseWaitingStatus.FAILED && TECHNICAL_FAILURE_CODE.equals(failureCode) && purchaseId != null) {
            status = PurchaseWaitingStatus.RESERVED;
            this.purchaseId = purchaseId;
            failureCode = null;
            completedAt = now;
            return;
        }
        completeReservation(purchaseId, now);
    }

    public void markSoldOut(String failureCode, LocalDateTime now) {
        complete(PurchaseWaitingStatus.SOLD_OUT, failureCode, now);
    }

    public void fail(String failureCode, LocalDateTime now) {
        complete(PurchaseWaitingStatus.FAILED, failureCode, now);
    }

    public void returnToWaiting() {
        if (status != PurchaseWaitingStatus.PROCESSING && status != PurchaseWaitingStatus.DISPATCHED) {
            throw PurchaseWaitingException.invalidState("Only active deliveries can return to waiting");
        }

        resetWaiting();
    }

    private void resetWaiting() {
        status = PurchaseWaitingStatus.WAITING;
        dispatchedAt = null;
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
