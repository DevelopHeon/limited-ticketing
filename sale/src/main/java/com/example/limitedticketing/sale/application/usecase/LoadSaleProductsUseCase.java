package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductPageResult;

public interface LoadSaleProductsUseCase {
    SaleProductPageResult loadPage(FindSaleProductsQuery query);
}
