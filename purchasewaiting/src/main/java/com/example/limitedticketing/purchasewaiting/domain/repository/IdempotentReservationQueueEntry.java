package com.example.limitedticketing.purchasewaiting.domain.repository;

import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;

public record IdempotentReservationQueueEntry(ReservationQueueEntry entry, boolean created) {
}
