package com.example.limitedticketing.shared.exception;

public final class SharedValueException extends DomainException {

    private SharedValueException(String code, String message) {
        super(DomainErrorType.INVALID_REQUEST, code, message);
    }

    public static SharedValueException invalidMoney() {
        return new SharedValueException("INVALID_MONEY", "Amount must be zero or positive");
    }

    public static SharedValueException invalidSalePeriod() {
        return new SharedValueException("INVALID_SALE_PERIOD", "Sale start time must be before end time");
    }
}
