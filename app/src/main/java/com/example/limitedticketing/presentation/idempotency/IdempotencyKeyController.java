package com.example.limitedticketing.presentation.idempotency;

import com.example.limitedticketing.presentation.idempotency.request.IssueIdempotencyKeyRequest;
import com.example.limitedticketing.presentation.idempotency.response.IdempotencyKeyResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!worker")
@RequestMapping("/api/idempotency-keys")
public class IdempotencyKeyController {

    private final IdempotencyKeyIssuer idempotencyKeyIssuer;

    public IdempotencyKeyController(IdempotencyKeyIssuer idempotencyKeyIssuer) {
        this.idempotencyKeyIssuer = idempotencyKeyIssuer;
    }

    @PostMapping
    public ApiResponse<IdempotencyKeyResponse> issue(
        @AuthenticationPrincipal(expression = "memberId") Long memberId,
        @Valid @RequestBody IssueIdempotencyKeyRequest request
    ) {
        String key = idempotencyKeyIssuer.issue(request.operation(), memberId);
        return ApiResponse.success(IdempotencyKeyResponse.of(key, request.operation()));
    }
}
