package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingWorkerPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.DispatchPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecoverPurchaseWaitingUseCase;
import org.springframework.stereotype.Component;

@Component
public class PurchaseWaitingWorkerAdapter implements PurchaseWaitingWorkerPort {

    private final DispatchPurchaseWaitingUseCase dispatchPurchaseWaitingUseCase;
    private final RecoverPurchaseWaitingUseCase recoverPurchaseWaitingUseCase;

    public PurchaseWaitingWorkerAdapter(
        DispatchPurchaseWaitingUseCase dispatchPurchaseWaitingUseCase,
        RecoverPurchaseWaitingUseCase recoverPurchaseWaitingUseCase
    ) {
        this.dispatchPurchaseWaitingUseCase = dispatchPurchaseWaitingUseCase;
        this.recoverPurchaseWaitingUseCase = recoverPurchaseWaitingUseCase;
    }

    @Override
    public void dispatchWaitingEntries() {
        dispatchPurchaseWaitingUseCase.dispatchWaitingEntries();
    }

    @Override
    public void recoverStaleEntries() {
        recoverPurchaseWaitingUseCase.recover();
    }
}
