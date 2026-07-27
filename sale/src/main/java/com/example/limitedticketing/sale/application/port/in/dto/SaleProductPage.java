package com.example.limitedticketing.sale.application.port.in.dto;

import java.util.List;

public record SaleProductPage(List<SaleProductSummary> items, int page, int size, long totalCount) {

    public static SaleProductPage of(List<SaleProductSummary> items, int page, int size, long totalCount) {
        return new SaleProductPage(items, page, size, totalCount);
    }
}
