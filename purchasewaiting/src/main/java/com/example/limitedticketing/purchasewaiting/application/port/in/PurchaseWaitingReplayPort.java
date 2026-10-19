package com.example.limitedticketing.purchasewaiting.application.port.in;

import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingReplayOutput;
import java.util.UUID;

public interface PurchaseWaitingReplayPort {
    PurchaseWaitingReplayOutput replay(UUID waitingEntryId);
}
