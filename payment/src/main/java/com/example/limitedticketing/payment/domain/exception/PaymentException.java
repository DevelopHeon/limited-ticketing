package com.example.limitedticketing.payment.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class PaymentException extends DomainException {

    private PaymentException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static PaymentException notFound() {
        return new PaymentException(DomainErrorType.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found");
    }

    public static PaymentException invalid(String message) {
        return new PaymentException(DomainErrorType.INVALID_REQUEST, "INVALID_PAYMENT", message);
    }

    public static PaymentException invalidState(String message) {
        return new PaymentException(DomainErrorType.CONFLICT, "INVALID_PAYMENT_STATE", message);
    }

    public static PaymentException reservationExpired() {
        return new PaymentException(
            DomainErrorType.CONFLICT,
            "PURCHASE_RESERVATION_EXPIRED",
            "Purchase reservation has expired"
        );
    }

    public static PaymentException ownershipViolation() {
        return new PaymentException(
            DomainErrorType.FORBIDDEN,
            "PAYMENT_OWNERSHIP_VIOLATION",
            "Only the purchase member can process payment"
        );
    }
}
