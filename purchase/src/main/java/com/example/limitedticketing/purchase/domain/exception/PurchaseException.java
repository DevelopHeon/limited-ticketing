package com.example.limitedticketing.purchase.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class PurchaseException extends DomainException {

    private PurchaseException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static PurchaseException notFound() {
        return new PurchaseException(DomainErrorType.NOT_FOUND, "PURCHASE_NOT_FOUND", "Purchase not found");
    }

    public static PurchaseException invalid(String message) {
        return new PurchaseException(DomainErrorType.INVALID_REQUEST, "INVALID_PURCHASE", message);
    }

    public static PurchaseException invalidState(String message) {
        return new PurchaseException(DomainErrorType.CONFLICT, "INVALID_PURCHASE_STATE", message);
    }

    public static PurchaseException activePurchaseExists() {
        return new PurchaseException(
            DomainErrorType.CONFLICT,
            "ACTIVE_PURCHASE_EXISTS",
            "An active purchase already exists for this sale product"
        );
    }

    public static PurchaseException requestKeyReuse() {
        return new PurchaseException(
            DomainErrorType.CONFLICT,
            "PURCHASE_REQUEST_KEY_REUSE",
            "Purchase request key cannot be reused for a different sale product"
        );
    }

    public static PurchaseException ownershipViolation() {
        return new PurchaseException(
            DomainErrorType.FORBIDDEN,
            "PURCHASE_OWNERSHIP_VIOLATION",
            "Only the purchase member can cancel a purchase"
        );
    }

    public static PurchaseException persistenceFailure() {
        return new PurchaseException(
            DomainErrorType.INTERNAL_ERROR,
            "PURCHASE_PERSISTENCE_FAILURE",
            "Unable to complete purchase persistence"
        );
    }
}
