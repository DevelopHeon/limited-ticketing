package com.example.limitedticketing.purchasewaiting.application.usecase;

import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import java.util.List;

public interface OutboxTransactionUseCase {
    List<ReservationOutboxClaim> claimReady(int limit);
    boolean complete(ReservationOutboxClaim claim);
    boolean recordFailure(ReservationOutboxClaim claim, Throwable failure);
}
