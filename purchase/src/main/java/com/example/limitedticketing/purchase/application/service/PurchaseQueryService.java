package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.usecase.FindPurchaseByRequestUseCase;
import com.example.limitedticketing.purchase.application.usecase.FindPurchaseForPaymentUseCase;
import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseByRequestQuery;
import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchasePaymentTargetResult;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PurchaseQueryService implements FindPurchaseForPaymentUseCase, FindPurchaseByRequestUseCase {

    private final PurchaseRepository purchaseRepository;

    public PurchaseQueryService(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    public PurchasePaymentTargetResult findForPayment(FindPurchaseForPaymentQuery query) {
        Purchase purchase = purchaseRepository.getRequiredById(query.purchaseId());

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

    @Override
    public Optional<Long> findPurchaseId(FindPurchaseByRequestQuery query) {
        return purchaseRepository.findByMemberIdAndPurchaseRequestKey(query.memberId(), query.purchaseRequestKey())
            .filter(purchase -> purchase.getSaleProductId().equals(query.saleProductId()))
            .map(Purchase::getId);
    }

}
