package com.example.limitedticketing.queue.application.query.dto;

import java.util.UUID;

public record FindReservationQueueEntryQuery(UUID queueEntryId, Long memberId) {
}
