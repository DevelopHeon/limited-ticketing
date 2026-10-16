package com.example.limitedticketing.purchasewaiting.application.port.out;

public interface ReservationQueueTaskExecutor {

    int availableSlots();

    void execute(Runnable task);
}
