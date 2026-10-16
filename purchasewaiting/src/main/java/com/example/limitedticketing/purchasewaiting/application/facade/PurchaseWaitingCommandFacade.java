package com.example.limitedticketing.purchasewaiting.application.facade;

import com.example.limitedticketing.purchasewaiting.application.port.in.PurchaseWaitingCommandPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.EnqueuePurchaseWaitingInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingOutput;
import com.example.limitedticketing.purchasewaiting.application.usecase.EnqueuePurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.FindPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.EnqueuePurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.query.FindPurchaseWaitingQuery;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PurchaseWaitingCommandFacade implements PurchaseWaitingCommandPort {

    private final EnqueuePurchaseWaitingUseCase enqueuePurchaseWaitingUseCase;
    private final FindPurchaseWaitingUseCase findPurchaseWaitingUseCase;

    public PurchaseWaitingCommandFacade(
        EnqueuePurchaseWaitingUseCase enqueuePurchaseWaitingUseCase,
        FindPurchaseWaitingUseCase findPurchaseWaitingUseCase
    ) {
        this.enqueuePurchaseWaitingUseCase = enqueuePurchaseWaitingUseCase;
        this.findPurchaseWaitingUseCase = findPurchaseWaitingUseCase;
    }

    @Override
    public PurchaseWaitingOutput enqueue(EnqueuePurchaseWaitingInput input) {
        UUID id = enqueuePurchaseWaitingUseCase.enqueue(new EnqueuePurchaseWaitingCommand(input.memberId(), input.saleProductId(), input.purchaseRequestKey()));
        PurchaseWaitingResult result = findPurchaseWaitingUseCase.find(new FindPurchaseWaitingQuery(id, input.memberId()));
        return new PurchaseWaitingOutput(result.waitingEntryId(), result.queueSequence(), result.status().name(),
            result.position(), result.purchaseId(), result.failureCode(), result.createdAt(),
            result.processingStartedAt(), result.completedAt());
    }
}
