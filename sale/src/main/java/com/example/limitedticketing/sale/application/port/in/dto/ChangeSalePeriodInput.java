package com.example.limitedticketing.sale.application.port.in.dto;

import java.time.LocalDateTime;

public record ChangeSalePeriodInput(Long saleProductId, LocalDateTime saleStartsAt, LocalDateTime saleEndsAt) {
}
