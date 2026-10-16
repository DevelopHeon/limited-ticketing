package com.example.limitedticketing.purchasewaiting.domain.repository;

import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;

public record PurchaseWaitingInsertResult(PurchaseWaitingEntry entry, boolean created) {
}
