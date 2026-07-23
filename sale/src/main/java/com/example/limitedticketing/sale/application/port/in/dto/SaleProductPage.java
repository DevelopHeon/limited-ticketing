package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.sale.application.query.dto.SaleProductPageResult;
import java.util.List;

public record SaleProductPage(List<SaleProductSummary> items, int page, int size, long totalCount) {

    public static SaleProductPage from(SaleProductPageResult result) {
        return new SaleProductPage(
            result.items().stream().map(SaleProductSummary::from).toList(),
            result.page(),
            result.size(),
            result.totalCount()
        );
    }
}
