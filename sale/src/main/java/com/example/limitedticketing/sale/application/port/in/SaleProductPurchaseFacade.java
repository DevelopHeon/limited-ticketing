package com.example.limitedticketing.sale.application.port.in;

import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.PurchasableSaleProduct;
import com.example.limitedticketing.sale.application.query.dto.FindPurchasableSaleProductQuery;
import com.example.limitedticketing.sale.application.query.dto.PurchasableSaleProductResult;
import com.example.limitedticketing.sale.application.query.service.SaleProductQueryService;
import org.springframework.stereotype.Component;

@Component
public class SaleProductPurchaseFacade {

    private final SaleProductQueryService saleProductQueryService;

    public SaleProductPurchaseFacade(SaleProductQueryService saleProductQueryService) {
        this.saleProductQueryService = saleProductQueryService;
    }

    public PurchasableSaleProduct findPurchasable(FindPurchasableSaleProductInput input) {
        PurchasableSaleProductResult result = saleProductQueryService.findPurchasable(
            new FindPurchasableSaleProductQuery(input.saleProductId(), input.requestedAt())
        );

        return new PurchasableSaleProduct(result.saleProductId(), result.price());
    }
}
