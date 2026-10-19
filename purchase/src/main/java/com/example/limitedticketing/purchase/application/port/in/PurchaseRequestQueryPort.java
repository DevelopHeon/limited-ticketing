package com.example.limitedticketing.purchase.application.port.in;

import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseByRequestInput;
import java.util.Optional;

public interface PurchaseRequestQueryPort {
    Optional<Long> findPurchaseId(FindPurchaseByRequestInput input);
}
