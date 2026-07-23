package com.example.limitedticketing.presentation.idempotency;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyIssuer {

    public String issue(IdempotencyOperation operation, Long memberId) {
        return operation.keyPrefix() + "_" + memberId + "_" + UUID.randomUUID();
    }
}
