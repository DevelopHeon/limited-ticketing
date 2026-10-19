package com.example.limitedticketing.purchasewaiting.application.port.out;

import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationJobEvent;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseWaitingOutboxPort {
    void append(ReservationJobEvent event);
    boolean containsRequest(UUID eventId, UUID entryId, Long saleProductId, UUID token);
    Optional<LocalDateTime> findPublishedAt(UUID entryId, UUID token);
    List<ReservationOutboxMessage> findReadyForUpdate(LocalDateTime now, int limit);
    void markPublished(UUID eventId, LocalDateTime now);
    void recordFailure(UUID eventId, LocalDateTime nextAttempt, String failureCode);
}
