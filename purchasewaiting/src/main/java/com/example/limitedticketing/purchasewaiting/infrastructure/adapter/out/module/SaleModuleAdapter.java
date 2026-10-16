package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.module;

import com.example.limitedticketing.purchasewaiting.application.port.out.SaleProductQueryPort;
import com.example.limitedticketing.sale.application.port.in.SaleProductPurchaseFacade;
import com.example.limitedticketing.sale.application.port.in.SaleProductQueryFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SaleModuleAdapter implements SaleProductQueryPort {

    private final SaleProductPurchaseFacade saleProductPurchaseFacade;
    private final SaleProductQueryFacade saleProductQueryFacade;

    public SaleModuleAdapter(
        SaleProductPurchaseFacade saleProductPurchaseFacade,
        SaleProductQueryFacade saleProductQueryFacade
    ) {
        this.saleProductPurchaseFacade = saleProductPurchaseFacade;
        this.saleProductQueryFacade = saleProductQueryFacade;
    }

    @Override
    public void ensurePurchasable(Long saleProductId, LocalDateTime requestedAt) {
        saleProductPurchaseFacade.findPurchasable(new FindPurchasableSaleProductInput(saleProductId, requestedAt));
    }

    @Override
    public boolean exists(Long saleProductId) {
        return saleProductQueryFacade.exists(new FindSaleProductInput(saleProductId));
    }

    @Override
    public boolean isClosed(Long saleProductId) {
        return saleProductQueryFacade.isClosed(new FindSaleProductInput(saleProductId));
    }
}
