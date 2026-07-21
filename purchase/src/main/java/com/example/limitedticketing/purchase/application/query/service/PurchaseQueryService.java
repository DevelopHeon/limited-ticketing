package com.example.limitedticketing.purchase.application.query.service;

import com.example.limitedticketing.purchase.application.query.dto.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.application.query.dto.PurchasePaymentTargetResult;
import com.example.limitedticketing.purchase.application.support.PurchaseFinder;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseQueryService {

    private final PurchaseFinder purchaseFinder;

    public PurchaseQueryService(PurchaseFinder purchaseFinder) {
        this.purchaseFinder = purchaseFinder;
    }

    public PurchasePaymentTargetResult findForPayment(FindPurchaseForPaymentQuery query) {
        Purchase purchase = purchaseFinder.getRequired(query.purchaseId());

        return new PurchasePaymentTargetResult(
            purchase.getId(),
            purchase.getMemberId(),
            purchase.getSaleProductId(),
            purchase.getPrice(),
            purchase.getQuantity(),
            purchase.getExpiresAt(),
            purchase.getStatus()
        );
    }
}
