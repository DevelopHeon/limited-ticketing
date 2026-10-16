package com.example.limitedticketing.sale.application.port.out;

public interface InventoryInitializationPort {

    void initialize(Long saleProductId, long quantity);
}
