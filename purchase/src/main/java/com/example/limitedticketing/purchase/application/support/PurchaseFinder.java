package com.example.limitedticketing.purchase.application.support;

import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import org.springframework.stereotype.Component;

@Component
public class PurchaseFinder {

    private final PurchaseRepository purchaseRepository;

    public PurchaseFinder(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    public Purchase getRequired(Long purchaseId) {
        return purchaseRepository.findById(purchaseId)
            .orElseThrow(PurchaseException::notFound);
    }
}
