package com.example.limitedticketing.purchasewaiting.application.port.out;

import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingClaim;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

public interface PurchaseWaitingQueuePort {

    void enqueue(PurchaseWaitingEntry entry);

    List<PurchaseWaitingClaim> claimNext(Long saleProductId, int maxConcurrentProcessing, int claimBatchSize, Instant claimedAt);

    void releaseProcessing(PurchaseWaitingEntry entry);

    void releaseProcessing(Long saleProductId, UUID queueEntryId, UUID token);

    void requeue(PurchaseWaitingEntry entry, UUID previousToken);

    void restoreWaiting(PurchaseWaitingEntry entry);

    boolean contains(Long saleProductId, UUID queueEntryId);

    OptionalLong findPosition(Long saleProductId, UUID queueEntryId);
}
