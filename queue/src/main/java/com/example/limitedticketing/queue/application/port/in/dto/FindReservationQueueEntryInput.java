package com.example.limitedticketing.queue.application.port.in.dto;

import java.util.UUID;

public record FindReservationQueueEntryInput(UUID queueEntryId, Long memberId) {
}
