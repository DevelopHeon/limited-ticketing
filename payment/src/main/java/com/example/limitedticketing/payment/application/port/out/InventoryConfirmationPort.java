package com.example.limitedticketing.payment.application.port.out;

public interface InventoryConfirmationPort {

    void confirmReservation(Long saleProductId, long quantity);
}
