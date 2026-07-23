package com.example.limitedticketing.sale.presentation.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record ChangeSalePeriodRequest(@NotNull LocalDateTime saleStartsAt, @NotNull LocalDateTime saleEndsAt) {
}
