package com.example.limitedticketing.purchase.domain.repository;

import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository {

    Purchase save(Purchase purchase);

    void transitionFromPending(Purchase purchase);

    IdempotentPurchase saveIfAbsent(Purchase purchase);

    Optional<Purchase> findById(Long purchaseId);

    default Purchase getRequiredById(Long purchaseId) {
        return findById(purchaseId).orElseThrow(PurchaseException::notFound);
    }

    Optional<Purchase> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    List<Purchase> findPendingPaymentExpiredAtOrBefore(LocalDateTime now);
}
