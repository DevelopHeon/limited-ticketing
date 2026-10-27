package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;

public interface IsSaleProductClosedUseCase {
    boolean isClosed(FindSaleProductQuery query);
}
