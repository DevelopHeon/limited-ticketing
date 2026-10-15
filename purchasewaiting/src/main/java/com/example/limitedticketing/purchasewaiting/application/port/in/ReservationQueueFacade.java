package com.example.limitedticketing.purchasewaiting.application.port.in;

import com.example.limitedticketing.purchasewaiting.application.command.dto.EnqueueReservationCommand;
import com.example.limitedticketing.purchasewaiting.application.command.dto.ReservationQueueEntryResult;
import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueCommandService;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.EnqueueReservationInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.FindReservationQueueEntryInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.application.query.dto.FindReservationQueueEntryQuery;
import com.example.limitedticketing.purchasewaiting.application.query.dto.ReservationQueueEntryDetailResult;
import com.example.limitedticketing.purchasewaiting.application.query.service.ReservationQueueQueryService;
import org.springframework.stereotype.Component;

@Component
public class ReservationQueueFacade {

    private final ReservationQueueCommandService reservationQueueCommandService;
    private final ReservationQueueQueryService reservationQueueQueryService;

    public ReservationQueueFacade(
        ReservationQueueCommandService reservationQueueCommandService,
        ReservationQueueQueryService reservationQueueQueryService
    ) {
        this.reservationQueueCommandService = reservationQueueCommandService;
        this.reservationQueueQueryService = reservationQueueQueryService;
    }

    public ReservationQueueEntry enqueue(EnqueueReservationInput input) {
        ReservationQueueEntryResult entry = reservationQueueCommandService.enqueue(
            new EnqueueReservationCommand(input.memberId(), input.saleProductId(), input.purchaseRequestKey())
        );
        return find(new FindReservationQueueEntryInput(entry.queueEntryId(), input.memberId()));
    }

    public ReservationQueueEntry find(FindReservationQueueEntryInput input) {
        ReservationQueueEntryDetailResult entry = reservationQueueQueryService.find(
            new FindReservationQueueEntryQuery(input.queueEntryId(), input.memberId())
        );
        return ReservationQueueEntry.of(
            entry.queueEntryId(),
            entry.queueSequence(),
            entry.status().name(),
            entry.position(),
            entry.purchaseId(),
            entry.failureCode(),
            entry.createdAt(),
            entry.processingStartedAt(),
            entry.completedAt()
        );
    }
}
