package com.example.limitedticketing.sale.infrastructure.adapter.in.web.response;

import com.example.limitedticketing.sale.application.port.in.dto.SaleProductPage;
import java.util.List;

public record SaleProductPageResponse(
    List<SaleProductSummaryResponse> items,
    int page,
    int size,
    long totalCount
) {

    public static SaleProductPageResponse from(SaleProductPage saleProductPage) {
        return new SaleProductPageResponse(
            saleProductPage.items().stream().map(SaleProductSummaryResponse::from).toList(),
            saleProductPage.page(),
            saleProductPage.size(),
            saleProductPage.totalCount()
        );
    }
}
