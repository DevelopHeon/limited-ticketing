package com.example.limitedticketing.purchasewaiting.application.port.out;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface ReservationQueueRecoveryStore {

    Set<Long> findSaleProductIds();

    Set<UUID> findWaitingEntryIds(Long saleProductId);

    Set<UUID> findProcessingEntryIds(Long saleProductId);

    void removeWaiting(Long saleProductId, Collection<UUID> queueEntryIds);

    void removeProcessing(Long saleProductId, Collection<UUID> queueEntryIds);

    void delete(Long saleProductId);
}
