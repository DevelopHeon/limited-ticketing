package com.example.limitedticketing.purchasewaiting.application.port.in.worker;

public interface PurchaseWaitingWorkerPort {

    void dispatchWaitingEntries();

    void recoverStaleEntries();
}
