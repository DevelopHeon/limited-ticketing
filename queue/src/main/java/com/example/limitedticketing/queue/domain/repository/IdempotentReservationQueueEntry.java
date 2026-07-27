package com.example.limitedticketing.queue.domain.repository;

import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;

public record IdempotentReservationQueueEntry(ReservationQueueEntry entry, boolean created) {
}
