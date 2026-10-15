package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReservationQueueProcessingService {

    private static final String INSUFFICIENT_INVENTORY = "INSUFFICIENT_INVENTORY";

    private final PurchaseCommandFacade purchaseCommandFacade;
    private final ReservationQueueCompletionService reservationQueueCompletionService;

    public ReservationQueueProcessingService(
        PurchaseCommandFacade purchaseCommandFacade,
        ReservationQueueCompletionService reservationQueueCompletionService
    ) {
        this.purchaseCommandFacade = purchaseCommandFacade;
        this.reservationQueueCompletionService = reservationQueueCompletionService;
    }

    public ReservationQueueEntry process(UUID queueEntryId) {
        try {
            PurchaseReservation reservation = purchaseCommandFacade.reserve(
                reservationQueueCompletionService.getReservationInput(queueEntryId)
            );
            return reservationQueueCompletionService.completeReservation(queueEntryId, reservation.purchaseId());
        } catch (DomainException exception) {
            if (INSUFFICIENT_INVENTORY.equals(exception.getCode())) {
                return reservationQueueCompletionService.completeSoldOut(queueEntryId, exception.getCode());
            } else {
                return reservationQueueCompletionService.completeFailure(queueEntryId, exception.getCode());
            }
        }
    }
}
