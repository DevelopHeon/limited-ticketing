package com.example.limitedticketing.purchasewaiting.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class PurchaseWaitingException extends DomainException {

    private PurchaseWaitingException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static PurchaseWaitingException capacityExceeded() {
        return new PurchaseWaitingException(DomainErrorType.TOO_MANY_REQUESTS,
            "QUEUE_CAPACITY_EXCEEDED", "Queue capacity is exhausted");
    }

    public static PurchaseWaitingException rateLimited() {
        return new PurchaseWaitingException(DomainErrorType.TOO_MANY_REQUESTS,
            "QUEUE_RATE_LIMITED", "Only one new entry per member and sale is allowed each second");
    }

    public static PurchaseWaitingException unavailable() {
        return new PurchaseWaitingException(DomainErrorType.SERVICE_UNAVAILABLE,
            "QUEUE_UNAVAILABLE", "Queue admission is temporarily unavailable");
    }

    public static PurchaseWaitingException notFound() {
        return new PurchaseWaitingException(DomainErrorType.NOT_FOUND, "QUEUE_ENTRY_NOT_FOUND", "Queue entry not found");
    }

    public static PurchaseWaitingException invalid(String message) {
        return new PurchaseWaitingException(DomainErrorType.INVALID_REQUEST, "INVALID_QUEUE_ENTRY", message);
    }

    public static PurchaseWaitingException invalidState(String message) {
        return new PurchaseWaitingException(DomainErrorType.CONFLICT, "INVALID_QUEUE_ENTRY_STATE", message);
    }

    public static PurchaseWaitingException activeEntryExists() {
        return new PurchaseWaitingException(
            DomainErrorType.CONFLICT,
            "ACTIVE_QUEUE_ENTRY_EXISTS",
            "An active queue entry already exists for this sale product"
        );
    }

    public static PurchaseWaitingException requestKeyReuse() {
        return new PurchaseWaitingException(
            DomainErrorType.CONFLICT,
            "QUEUE_REQUEST_KEY_REUSE",
            "Queue request key cannot be reused for a different sale product"
        );
    }

    public static PurchaseWaitingException ownershipViolation() {
        return new PurchaseWaitingException(
            DomainErrorType.FORBIDDEN,
            "QUEUE_ENTRY_OWNERSHIP_VIOLATION",
            "Only the queue entry member can view this queue entry"
        );
    }

    public static PurchaseWaitingException persistenceFailure() {
        return new PurchaseWaitingException(
            DomainErrorType.INTERNAL_ERROR,
            "QUEUE_ENTRY_PERSISTENCE_FAILURE",
            "Unable to complete queue entry persistence"
        );
    }
}
