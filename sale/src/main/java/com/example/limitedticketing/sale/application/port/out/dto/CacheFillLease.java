package com.example.limitedticketing.sale.application.port.out.dto;

import java.util.UUID;

public record CacheFillLease(UUID generation, UUID token, long freshnessDeadlineEpochMillis) {
}
