package com.example.limitedticketing.shared.exception;

public abstract class DomainException extends RuntimeException {

    private final DomainErrorType errorType;
    private final String code;

    protected DomainException(DomainErrorType errorType, String code, String message) {
        super(message);
        this.errorType = errorType;
        this.code = code;
    }

    public DomainErrorType getErrorType() {
        return errorType;
    }

    public String getCode() {
        return code;
    }
}
