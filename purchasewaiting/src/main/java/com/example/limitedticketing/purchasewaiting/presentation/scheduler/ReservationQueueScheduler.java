package com.example.limitedticketing.purchasewaiting.presentation.scheduler;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.ReservationQueueWorkerFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueScheduler {

    private final ReservationQueueWorkerFacade worker;

    public ReservationQueueScheduler(ReservationQueueWorkerFacade worker) {
        this.worker = worker;
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.queue.worker.fixed-delay}",
        initialDelayString = "${limited-ticketing.queue.worker.initial-delay}"
    )
    public void dispatchWaitingEntries() {
        worker.dispatchWaitingEntries();
    }

    @Scheduled(
        fixedDelayString = "${limited-ticketing.queue.worker.recovery-fixed-delay}",
        initialDelayString = "${limited-ticketing.queue.worker.recovery-initial-delay}"
    )
    public void recoverStaleEntries() {
        worker.recoverStaleEntries();
    }
}
