package com.example.limitedticketing.purchasewaiting.application.support;

import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueEntryFinder {

    private final ReservationQueueEntryRepository reservationQueueEntryRepository;

    public ReservationQueueEntryFinder(ReservationQueueEntryRepository reservationQueueEntryRepository) {
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
    }

    public ReservationQueueEntry getRequired(UUID queueEntryId) {
        return reservationQueueEntryRepository.findById(queueEntryId)
            .orElseThrow(ReservationQueueException::notFound);
    }
}
