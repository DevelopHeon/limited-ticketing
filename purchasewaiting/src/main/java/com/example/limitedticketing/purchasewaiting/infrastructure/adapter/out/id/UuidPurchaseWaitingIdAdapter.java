package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.id;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidPurchaseWaitingIdAdapter implements PurchaseWaitingIdGeneratorPort {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
