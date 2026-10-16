package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface SaleProductQueryPort {

    void ensurePurchasable(Long saleProductId, java.time.LocalDateTime requestedAt);

    boolean exists(Long saleProductId);

    boolean isClosed(Long saleProductId);
}
