package com.example.limitedticketing.presentation.idempotency;

public enum IdempotencyOperation {
    PURCHASE("purchase"),
    PAYMENT("payment");

    private final String keyPrefix;

    IdempotencyOperation(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    String keyPrefix() {
        return keyPrefix;
    }
}
