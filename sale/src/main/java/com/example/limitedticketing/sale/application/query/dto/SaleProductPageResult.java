package com.example.limitedticketing.sale.application.query.dto;

import java.util.List;

public record SaleProductPageResult(
    List<SaleProductSummaryResult> items,
    int page,
    int size,
    long totalCount
) {
}
