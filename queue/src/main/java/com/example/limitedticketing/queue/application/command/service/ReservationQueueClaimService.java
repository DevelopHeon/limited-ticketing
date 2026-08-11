package com.example.limitedticketing.queue.application.command.service;

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

    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final Clock clock;

    public ReservationQueueClaimService(
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        Clock clock
    ) {
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<ReservationQueueEntry> claim(UUID queueEntryId) {
        return reservationQueueEntryRepository.findById(queueEntryId)
            .filter(entry -> entry.getStatus() == ReservationQueueStatus.WAITING)
            .map(entry -> {
                entry.beginProcessing(LocalDateTime.now(clock));
                return reservationQueueEntryRepository.save(entry);
            });
    }
}
