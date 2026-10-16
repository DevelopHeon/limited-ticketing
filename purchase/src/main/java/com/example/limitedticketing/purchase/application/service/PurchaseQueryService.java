package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.usecase.FindPurchaseForPaymentUseCase;
import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchasePaymentTargetResult;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseQueryService implements FindPurchaseForPaymentUseCase {

    private final PurchaseRepository purchaseRepository;

    public PurchaseQueryService(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    public PurchasePaymentTargetResult findForPayment(FindPurchaseForPaymentQuery query) {
        Purchase purchase = getRequiredPurchase(query.purchaseId());

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

    private Purchase getRequiredPurchase(Long id) {
        return purchaseRepository.findById(id).orElseThrow(PurchaseException::notFound);
    }
}
