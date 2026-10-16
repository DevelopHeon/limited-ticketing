package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface PurchaseWaitingTaskExecutionPort {

    int availableSlots();

    void execute(Runnable task);
}
