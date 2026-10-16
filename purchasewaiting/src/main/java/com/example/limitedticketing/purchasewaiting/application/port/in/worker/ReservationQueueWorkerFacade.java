package com.example.limitedticketing.purchasewaiting.application.port.in.worker;

import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueDispatchService;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueWorkerFacade {

    private final ReservationQueueDispatchService dispatchService;

    public ReservationQueueWorkerFacade(ReservationQueueDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public void dispatchWaitingEntries() {
        dispatchService.dispatchWaitingEntries();
    }

    public void recoverStaleEntries() {
        dispatchService.recoverStaleEntries();
    }
}
