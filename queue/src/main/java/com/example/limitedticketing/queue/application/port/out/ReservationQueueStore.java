package com.example.limitedticketing.queue.application.port.out;

import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

public interface ReservationQueueStore {

    long nextSequence(Long saleProductId);

    void enqueue(ReservationQueueEntry entry);

    List<UUID> claimNext(Long saleProductId, int maxConcurrentProcessing, int claimBatchSize, Instant claimedAt);

    void releaseProcessing(ReservationQueueEntry entry);

    void releaseProcessing(Long saleProductId, UUID queueEntryId);

    void requeue(ReservationQueueEntry entry);

    boolean contains(Long saleProductId, UUID queueEntryId);

    void removeProcessingOlderThan(Long saleProductId, Instant threshold);

    OptionalLong findPosition(Long saleProductId, UUID queueEntryId);
}
