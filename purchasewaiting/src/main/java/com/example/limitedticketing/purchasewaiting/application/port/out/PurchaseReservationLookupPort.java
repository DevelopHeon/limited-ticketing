package com.example.limitedticketing.purchasewaiting.application.port.out;

import java.util.Optional;

public interface PurchaseReservationLookupPort {
    Optional<Long> findPurchaseId(Long memberId, Long saleProductId, String purchaseRequestKey);
}
