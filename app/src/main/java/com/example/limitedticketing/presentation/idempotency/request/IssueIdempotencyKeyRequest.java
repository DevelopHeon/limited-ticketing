package com.example.limitedticketing.presentation.idempotency.request;

import com.example.limitedticketing.presentation.idempotency.IdempotencyOperation;
import jakarta.validation.constraints.NotNull;

public record IssueIdempotencyKeyRequest(@NotNull IdempotencyOperation operation) {
}
