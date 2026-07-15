package com.example.limitedticketing.purchase.application.port.out;

import com.example.limitedticketing.purchase.domain.event.PurchaseReserved;

public interface PurchaseEventPublisher {

    void publish(PurchaseReserved event);
}
