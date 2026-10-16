package com.example.limitedticketing.purchasewaiting.application.port.out.dto;

import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;

public record PurchaseWaitingInsertResult(PurchaseWaitingEntry entry, boolean created) {
}
