package com.example.limitedticketing.purchase.domain.repository;

import com.example.limitedticketing.purchase.domain.model.Purchase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository {

    Purchase save(Purchase purchase);

    Optional<Purchase> findById(Long purchaseId);

    List<Purchase> findPendingPaymentExpiredAtOrBefore(LocalDateTime now);
}
