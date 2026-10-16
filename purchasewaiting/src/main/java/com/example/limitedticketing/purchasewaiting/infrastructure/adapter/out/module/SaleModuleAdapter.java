package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.module;

import com.example.limitedticketing.sale.application.port.in.SaleProductPurchasePort;
import com.example.limitedticketing.sale.application.port.in.SaleProductQueryPort;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SaleModuleAdapter implements com.example.limitedticketing.purchasewaiting.application.port.out.SaleProductQueryPort {

    private final SaleProductPurchasePort saleProductPurchasePort;
    private final SaleProductQueryPort saleProductQueryPort;

    public SaleModuleAdapter(
        SaleProductPurchasePort saleProductPurchasePort,
        SaleProductQueryPort saleProductQueryPort
    ) {
        this.saleProductPurchasePort = saleProductPurchasePort;
        this.saleProductQueryPort = saleProductQueryPort;
    }

    @Override
    public void ensurePurchasable(Long saleProductId, LocalDateTime requestedAt) {
        saleProductPurchasePort.findPurchasable(new FindPurchasableSaleProductInput(saleProductId, requestedAt));
    }

    @Override
    public boolean exists(Long saleProductId) {
        return saleProductQueryPort.exists(new FindSaleProductInput(saleProductId));
    }

    @Override
    public boolean isClosed(Long saleProductId) {
        return saleProductQueryPort.isClosed(new FindSaleProductInput(saleProductId));
    }
}
