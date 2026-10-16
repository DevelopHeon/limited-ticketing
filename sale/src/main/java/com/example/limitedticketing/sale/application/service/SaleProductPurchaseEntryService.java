package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.in.SaleProductPurchasePort;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.PurchasableSaleProduct;
import com.example.limitedticketing.sale.application.usecase.FindPurchasableSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindPurchasableSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.PurchasableSaleProductResult;
import org.springframework.stereotype.Service;

@Service
public class SaleProductPurchaseEntryService implements SaleProductPurchasePort {

    private final FindPurchasableSaleProductUseCase findPurchasableSaleProductUseCase;

    public SaleProductPurchaseEntryService(FindPurchasableSaleProductUseCase findPurchasableSaleProductUseCase) {
        this.findPurchasableSaleProductUseCase = findPurchasableSaleProductUseCase;
    }

    public PurchasableSaleProduct findPurchasable(FindPurchasableSaleProductInput input) {
        PurchasableSaleProductResult result = findPurchasableSaleProductUseCase.findPurchasable(
            new FindPurchasableSaleProductQuery(input.saleProductId(), input.requestedAt())
        );

        return new PurchasableSaleProduct(result.saleProductId(), result.price());
    }
}
