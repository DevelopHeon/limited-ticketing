package com.example.limitedticketing.queue.application.command.service;

import com.example.limitedticketing.queue.application.support.ReservationQueueEntryFinder;
import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.queue.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.queue.domain.repository.ReservationQueueEntryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationQueueClaimService {

    private final ReservationQueueEntryFinder reservationQueueEntryFinder;
    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final Clock clock;

    public ReservationQueueClaimService(
        ReservationQueueEntryFinder reservationQueueEntryFinder,
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        Clock clock
    ) {
        this.reservationQueueEntryFinder = reservationQueueEntryFinder;
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<ReservationQueueEntry> claim(UUID queueEntryId) {
        ReservationQueueEntry entry = reservationQueueEntryFinder.getRequired(queueEntryId);
        if (entry.getStatus() != ReservationQueueStatus.WAITING) {
            return Optional.empty();
        }

        entry.beginProcessing(LocalDateTime.now(clock));
        return Optional.of(reservationQueueEntryRepository.save(entry));
    }
}
