package com.example.limitedticketing.presentation.idempotency.response;

import com.example.limitedticketing.presentation.idempotency.IdempotencyOperation;

public record IdempotencyKeyResponse(String idempotencyKey, IdempotencyOperation operation) {

    public static IdempotencyKeyResponse of(String idempotencyKey, IdempotencyOperation operation) {
        return new IdempotencyKeyResponse(idempotencyKey, operation);
    }
}
