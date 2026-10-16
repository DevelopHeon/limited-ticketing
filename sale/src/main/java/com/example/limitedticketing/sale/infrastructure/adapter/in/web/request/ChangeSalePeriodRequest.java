package com.example.limitedticketing.sale.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ChangeSalePeriodRequest(@NotNull LocalDateTime saleStartsAt, @NotNull LocalDateTime saleEndsAt) {
}
