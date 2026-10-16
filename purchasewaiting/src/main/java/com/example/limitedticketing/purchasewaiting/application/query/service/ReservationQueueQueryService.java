package com.example.limitedticketing.purchasewaiting.application.query.service;

import com.example.limitedticketing.purchasewaiting.application.command.dto.ReservationQueueEntryResult;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.application.query.dto.FindReservationQueueEntryQuery;
import com.example.limitedticketing.purchasewaiting.application.query.dto.ReservationQueueEntryDetailResult;
import com.example.limitedticketing.purchasewaiting.application.support.ReservationQueueEntryFinder;
import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import java.util.OptionalLong;
import java.util.UUID;
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

    private OptionalLong findPosition(Long saleProductId, UUID entryId) {
        try {
            return reservationQueueStore.findPosition(saleProductId, entryId);
        } catch (ReservationQueueException exception) {
            if (!"QUEUE_UNAVAILABLE".equals(exception.getCode())) {
                throw exception;
            }
            return OptionalLong.empty();
        }
    }

    @Transactional(readOnly = true)
    public ReservationQueueEntryDetailResult find(FindReservationQueueEntryQuery query) {
        ReservationQueueEntry entry = reservationQueueEntryFinder.getRequired(query.queueEntryId());
        if (!entry.getMemberId().equals(query.memberId())) {
            throw ReservationQueueException.ownershipViolation();
        }

        OptionalLong position = entry.getStatus() == ReservationQueueStatus.WAITING
            ? findPosition(entry.getSaleProductId(), entry.getId())
            : OptionalLong.empty();

        return ReservationQueueEntryDetailResult.from(
            ReservationQueueEntryResult.from(entry),
            position.isPresent() ? position.getAsLong() : null
        );
    }
}
