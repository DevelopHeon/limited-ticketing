package com.example.limitedticketing.purchasewaiting.application.usecase.dto.query;

import java.util.UUID;

public record FindPurchaseWaitingQuery(UUID waitingEntryId, Long memberId) {
}
