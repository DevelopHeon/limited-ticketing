package com.example.limitedticketing.purchasewaiting.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class ReservationQueueException extends DomainException {

    private ReservationQueueException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static ReservationQueueException capacityExceeded() {
        return new ReservationQueueException(DomainErrorType.TOO_MANY_REQUESTS,
            "QUEUE_CAPACITY_EXCEEDED", "Queue capacity is exhausted");
    }

    public static ReservationQueueException rateLimited() {
        return new ReservationQueueException(DomainErrorType.TOO_MANY_REQUESTS,
            "QUEUE_RATE_LIMITED", "Only one new entry per member and sale is allowed each second");
    }

    public static ReservationQueueException unavailable() {
        return new ReservationQueueException(DomainErrorType.SERVICE_UNAVAILABLE,
            "QUEUE_UNAVAILABLE", "Queue admission is temporarily unavailable");
    }

    public static ReservationQueueException notFound() {
        return new ReservationQueueException(DomainErrorType.NOT_FOUND, "QUEUE_ENTRY_NOT_FOUND", "Queue entry not found");
    }

    public static ReservationQueueException invalid(String message) {
        return new ReservationQueueException(DomainErrorType.INVALID_REQUEST, "INVALID_QUEUE_ENTRY", message);
    }

    public static ReservationQueueException invalidState(String message) {
        return new ReservationQueueException(DomainErrorType.CONFLICT, "INVALID_QUEUE_ENTRY_STATE", message);
    }

    public static ReservationQueueException activeEntryExists() {
        return new ReservationQueueException(
            DomainErrorType.CONFLICT,
            "ACTIVE_QUEUE_ENTRY_EXISTS",
            "An active queue entry already exists for this sale product"
        );
    }

    public static ReservationQueueException requestKeyReuse() {
        return new ReservationQueueException(
            DomainErrorType.CONFLICT,
            "QUEUE_REQUEST_KEY_REUSE",
            "Queue request key cannot be reused for a different sale product"
        );
    }

    public static ReservationQueueException ownershipViolation() {
        return new ReservationQueueException(
            DomainErrorType.FORBIDDEN,
            "QUEUE_ENTRY_OWNERSHIP_VIOLATION",
            "Only the queue entry member can view this queue entry"
        );
    }

    public static ReservationQueueException persistenceFailure() {
        return new ReservationQueueException(
            DomainErrorType.INTERNAL_ERROR,
            "QUEUE_ENTRY_PERSISTENCE_FAILURE",
            "Unable to complete queue entry persistence"
        );
    }
}
