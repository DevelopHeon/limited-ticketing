package com.example.limitedticketing.purchase.application.port.out;

public interface InventoryReservationPort {

    void reserve(Long saleProductId, long quantity);

    void releaseReservation(Long saleProductId, long quantity);
}
