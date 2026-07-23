package com.example.limitedticketing.sale.application.command.dto;

import java.time.LocalDateTime;

public record ChangeSalePeriodCommand(Long saleProductId, LocalDateTime saleStartsAt, LocalDateTime saleEndsAt) {
}
