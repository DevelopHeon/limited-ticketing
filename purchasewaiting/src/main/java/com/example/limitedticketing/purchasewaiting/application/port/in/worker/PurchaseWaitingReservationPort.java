package com.example.limitedticketing.purchasewaiting.application.port.in.worker;

import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobOutcome;

public interface PurchaseWaitingReservationPort {
    ReservationJobOutcome execute(ReservationJobInput input);
}
