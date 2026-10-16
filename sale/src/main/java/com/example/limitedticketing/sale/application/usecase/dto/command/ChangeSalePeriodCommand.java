package com.example.limitedticketing.sale.application.usecase.dto.command;

import java.time.LocalDateTime;

public record ChangeSalePeriodCommand(Long saleProductId, LocalDateTime saleStartsAt, LocalDateTime saleEndsAt) {
}
