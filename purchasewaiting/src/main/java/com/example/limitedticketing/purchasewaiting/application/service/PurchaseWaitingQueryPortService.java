package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.in.PurchaseWaitingQueryPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.FindPurchaseWaitingInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingOutput;
import com.example.limitedticketing.purchasewaiting.application.usecase.FindPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.query.FindPurchaseWaitingQuery;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;
import org.springframework.stereotype.Service;

@Service
public class PurchaseWaitingQueryPortService implements PurchaseWaitingQueryPort {

    private final FindPurchaseWaitingUseCase findPurchaseWaitingUseCase;

    public PurchaseWaitingQueryPortService(FindPurchaseWaitingUseCase findPurchaseWaitingUseCase) {
        this.findPurchaseWaitingUseCase = findPurchaseWaitingUseCase;
    }

    @Override
    public PurchaseWaitingOutput find(FindPurchaseWaitingInput input) {
        PurchaseWaitingResult result = findPurchaseWaitingUseCase.find(new FindPurchaseWaitingQuery(input.waitingEntryId(), input.memberId()));
        return new PurchaseWaitingOutput(result.waitingEntryId(), result.queueSequence(), result.status().name(),
            result.position(), result.purchaseId(), result.failureCode(), result.createdAt(),
            result.processingStartedAt(), result.completedAt());
    }
}
