package com.example.limitedticketing.purchasewaiting.application.port.out;

import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationJobEvent;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseWaitingOutboxPort {
    void append(ReservationJobEvent event);
    boolean containsRequest(UUID eventId, UUID entryId, Long saleProductId, UUID token);
    Optional<LocalDateTime> findPublishedAt(UUID entryId, UUID token);
    List<ReservationOutboxClaim> claimReady(UUID claimId, LocalDateTime now, LocalDateTime claimUntil, int limit);
    boolean markPublished(UUID eventId, UUID claimId, LocalDateTime now);
    boolean recordFailure(UUID eventId, UUID claimId, LocalDateTime now, LocalDateTime nextAttempt, String failureCode);
}
