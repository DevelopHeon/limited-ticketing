package com.example.limitedticketing.sale.application.port.out.dto;

import java.util.List;

public record CachedSaleProductPage(List<CachedSaleProductSummary> items, int page, int size, long totalCount) {
}
