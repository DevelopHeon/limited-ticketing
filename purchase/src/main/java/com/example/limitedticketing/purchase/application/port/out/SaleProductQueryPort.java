package com.example.limitedticketing.purchase.application.port.out;

import com.example.limitedticketing.purchase.application.port.out.dto.PurchasableSaleProduct;
import java.time.LocalDateTime;

public interface SaleProductQueryPort {

    PurchasableSaleProduct findPurchasable(Long saleProductId, LocalDateTime requestedAt);
}
