package com.example.limitedticketing.purchase.infrastructure.adapter.out.module;

import com.example.limitedticketing.purchase.application.port.out.SaleProductQueryPort;
import com.example.limitedticketing.purchase.application.port.out.dto.PurchasableSaleProduct;
import com.example.limitedticketing.sale.application.port.in.SaleProductPurchasePort;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class PurchaseSaleModuleAdapter implements SaleProductQueryPort {

    private final SaleProductPurchasePort saleProductPurchasePort;

    public PurchaseSaleModuleAdapter(SaleProductPurchasePort saleProductPurchasePort) {
        this.saleProductPurchasePort = saleProductPurchasePort;
    }

    @Override
    public PurchasableSaleProduct findPurchasable(Long saleProductId, LocalDateTime requestedAt) {
        com.example.limitedticketing.sale.application.port.in.dto.PurchasableSaleProduct result =
            saleProductPurchasePort.findPurchasable(new FindPurchasableSaleProductInput(saleProductId, requestedAt));
        return new PurchasableSaleProduct(result.saleProductId(), result.price());
    }
}
