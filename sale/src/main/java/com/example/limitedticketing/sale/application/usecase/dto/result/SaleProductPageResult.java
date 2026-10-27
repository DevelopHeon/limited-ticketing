package com.example.limitedticketing.sale.application.usecase.dto.result;

import java.util.List;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductPage;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductSummary;

public record SaleProductPageResult(
    List<SaleProductSummaryResult> items,
    int page,
    int size,
    long totalCount
) {
    public CachedSaleProductPage toCacheValue() {
        return new CachedSaleProductPage(items.stream().map(item -> new CachedSaleProductSummary(item.saleProductId(),
            item.productName(), item.salePrice(), item.saleStartsAt(), item.saleEndsAt(), item.status())).toList(), page, size, totalCount);
    }

    public static SaleProductPageResult fromCache(CachedSaleProductPage value) {
        return new SaleProductPageResult(value.items().stream().map(item -> new SaleProductSummaryResult(item.saleProductId(),
            item.productName(), item.salePrice(), item.saleStartsAt(), item.saleEndsAt(), item.status())).toList(),
            value.page(), value.size(), value.totalCount());
    }
}
