package com.example.limitedticketing.purchasewaiting.application.query.dto;

import java.util.UUID;

public record FindReservationQueueEntryQuery(UUID queueEntryId, Long memberId) {
}
