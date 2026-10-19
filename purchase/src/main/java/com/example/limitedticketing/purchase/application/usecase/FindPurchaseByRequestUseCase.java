package com.example.limitedticketing.purchase.application.usecase;

import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseByRequestQuery;
import java.util.Optional;

public interface FindPurchaseByRequestUseCase {
    Optional<Long> findPurchaseId(FindPurchaseByRequestQuery query);
}
