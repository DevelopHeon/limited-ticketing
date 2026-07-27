package com.example.limitedticketing.queue.application.query.service;

import com.example.limitedticketing.queue.application.command.dto.ReservationQueueEntryResult;
import com.example.limitedticketing.queue.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.queue.application.query.dto.FindReservationQueueEntryQuery;
import com.example.limitedticketing.queue.application.query.dto.ReservationQueueEntryDetailResult;
import com.example.limitedticketing.queue.application.support.ReservationQueueEntryFinder;
import com.example.limitedticketing.queue.domain.exception.ReservationQueueException;
import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.queue.domain.model.ReservationQueueStatus;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationQueueQueryService {

    private final ReservationQueueEntryFinder reservationQueueEntryFinder;
    private final ReservationQueueStore reservationQueueStore;

    public ReservationQueueQueryService(
        ReservationQueueEntryFinder reservationQueueEntryFinder,
        ReservationQueueStore reservationQueueStore
    ) {
        this.reservationQueueEntryFinder = reservationQueueEntryFinder;
        this.reservationQueueStore = reservationQueueStore;
    }

    @Transactional(readOnly = true)
    public ReservationQueueEntryDetailResult find(FindReservationQueueEntryQuery query) {
        ReservationQueueEntry entry = reservationQueueEntryFinder.getRequired(query.queueEntryId());
        if (!entry.getMemberId().equals(query.memberId())) {
            throw ReservationQueueException.ownershipViolation();
        }

        OptionalLong position = entry.getStatus() == ReservationQueueStatus.WAITING
            ? reservationQueueStore.findPosition(entry.getSaleProductId(), entry.getId())
            : OptionalLong.empty();

        return ReservationQueueEntryDetailResult.from(
            ReservationQueueEntryResult.from(entry),
            position.isPresent() ? position.getAsLong() : null
        );
    }
}
