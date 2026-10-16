package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingSequencePort;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.PurchaseWaitingJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class PostgresPurchaseWaitingSequenceAdapter implements PurchaseWaitingSequencePort {

    private final PurchaseWaitingJpaRepository purchaseWaitingJpaRepository;

    public PostgresPurchaseWaitingSequenceAdapter(PurchaseWaitingJpaRepository purchaseWaitingJpaRepository) {
        this.purchaseWaitingJpaRepository = purchaseWaitingJpaRepository;
    }

    @Override
    public long nextSequence(Long saleProductId) {
        return purchaseWaitingJpaRepository.nextSequence(saleProductId);
    }
}
