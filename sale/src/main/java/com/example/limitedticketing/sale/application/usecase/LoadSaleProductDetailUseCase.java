package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductDetailResult;

public interface LoadSaleProductDetailUseCase {
    SaleProductDetailResult loadDetail(FindSaleProductQuery query);
}
