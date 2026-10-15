package com.example.limitedticketing.purchasewaiting.application.port.in.dto;

import java.util.UUID;

public record FindReservationQueueEntryInput(UUID queueEntryId, Long memberId) {
}
