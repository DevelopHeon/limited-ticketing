package com.example.limitedticketing.sale.application.usecase.dto.result;

import java.util.List;

public record SaleProductPageResult(
    List<SaleProductSummaryResult> items,
    int page,
    int size,
    long totalCount
) {
}
