package com.example.limitedticketing.purchasewaiting.application.port.out;

import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingInsertResult;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseWaitingEntryCommandPort {

    void lockQueue(Long saleProductId);

    Optional<PurchaseWaitingEntry> claim(UUID queueEntryId, UUID token, LocalDateTime now, LocalDateTime leaseUntil);

    PurchaseWaitingEntry save(PurchaseWaitingEntry entry);

    PurchaseWaitingInsertResult saveIfAbsent(PurchaseWaitingEntry entry);
}
