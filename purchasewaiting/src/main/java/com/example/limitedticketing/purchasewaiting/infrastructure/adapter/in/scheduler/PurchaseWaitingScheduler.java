package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.scheduler;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingWorkerPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurchaseWaitingScheduler {

    private final PurchaseWaitingWorkerPort purchaseWaitingWorkerPort;

    public PurchaseWaitingScheduler(PurchaseWaitingWorkerPort purchaseWaitingWorkerPort) {
        this.purchaseWaitingWorkerPort = purchaseWaitingWorkerPort;
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.queue.worker.fixed-delay}",
        initialDelayString = "${limited-ticketing.queue.worker.initial-delay}"
    )
    public void dispatchWaitingEntries() {
        purchaseWaitingWorkerPort.dispatchWaitingEntries();
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.queue.worker.recovery-fixed-delay}",
        initialDelayString = "${limited-ticketing.queue.worker.recovery-initial-delay}"
    )
    public void recoverStaleEntries() {
        purchaseWaitingWorkerPort.recoverStaleEntries();
    }
}
