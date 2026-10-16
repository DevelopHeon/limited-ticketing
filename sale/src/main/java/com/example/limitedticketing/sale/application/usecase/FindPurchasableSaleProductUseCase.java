package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.query.FindPurchasableSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.PurchasableSaleProductResult;

public interface FindPurchasableSaleProductUseCase {

    PurchasableSaleProductResult findPurchasable(FindPurchasableSaleProductQuery query);
}
