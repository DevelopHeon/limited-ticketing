package com.example.limitedticketing.queue.application.support;

import com.example.limitedticketing.queue.domain.exception.ReservationQueueException;
import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.queue.domain.repository.ReservationQueueEntryRepository;
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
