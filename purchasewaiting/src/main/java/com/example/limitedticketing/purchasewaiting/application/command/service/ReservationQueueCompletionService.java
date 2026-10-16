package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchasewaiting.application.support.ReservationQueueEntryFinder;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReservationQueueCompletionService {

    private final ReservationQueueEntryFinder reservationQueueEntryFinder;
    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final Clock clock;

    ReservationQueueCompletionService(
        ReservationQueueEntryFinder reservationQueueEntryFinder,
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        Clock clock
    ) {
        this.reservationQueueEntryFinder = reservationQueueEntryFinder;
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<ReservePurchaseInput> getReservationInput(UUID id, UUID token) {
        return reservationQueueEntryRepository.findByIdForUpdate(id)
            .filter(entry -> entry.ownsAttempt(token) && entry.getLeaseUntil().isAfter(LocalDateTime.now(clock)))
            .map(entry -> new ReservePurchaseInput(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey()));
    }

    @Transactional
    public ReservationQueueEntry completeReservation(UUID queueEntryId, Long purchaseId) {
        ReservationQueueEntry entry = reservationQueueEntryFinder.getRequired(queueEntryId);
        entry.completeReservation(purchaseId, LocalDateTime.now(clock));
        return reservationQueueEntryRepository.save(entry);
    }

    @Transactional
    public ReservationQueueEntry completeSoldOut(UUID queueEntryId, String failureCode) {
        ReservationQueueEntry entry = reservationQueueEntryFinder.getRequired(queueEntryId);
        entry.markSoldOut(failureCode, LocalDateTime.now(clock));
        return reservationQueueEntryRepository.save(entry);
    }

    @Transactional
    public ReservationQueueEntry completeFailure(UUID queueEntryId, String failureCode) {
        ReservationQueueEntry entry = reservationQueueEntryFinder.getRequired(queueEntryId);
        entry.fail(failureCode, LocalDateTime.now(clock));
        return reservationQueueEntryRepository.save(entry);
    }
}
