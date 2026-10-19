package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.port.in.PurchaseRequestQueryPort;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseByRequestInput;
import com.example.limitedticketing.purchase.application.usecase.FindPurchaseByRequestUseCase;
import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseByRequestQuery;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PurchaseRequestQueryPortService implements PurchaseRequestQueryPort {
    private final FindPurchaseByRequestUseCase findPurchaseByRequestUseCase;
    public PurchaseRequestQueryPortService(FindPurchaseByRequestUseCase findPurchaseByRequestUseCase) {
        this.findPurchaseByRequestUseCase = findPurchaseByRequestUseCase;
    }
    @Override
    public Optional<Long> findPurchaseId(FindPurchaseByRequestInput input) {
        return findPurchaseByRequestUseCase.findPurchaseId(new FindPurchaseByRequestQuery(input.memberId(), input.saleProductId(), input.purchaseRequestKey()));
    }
}
