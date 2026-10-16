package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.shared.exception.DomainException;
import java.util.UUID;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ReservationQueueProcessingService {

    private static final String INSUFFICIENT_INVENTORY = "INSUFFICIENT_INVENTORY";

    private final ReservationQueuePurchaseService purchaseService;
    private final ReservationQueueCompletionService reservationQueueCompletionService;

    public ReservationQueueProcessingService(
        ReservationQueuePurchaseService purchaseService,
        ReservationQueueCompletionService reservationQueueCompletionService
    ) {
        this.purchaseService = purchaseService;
        this.reservationQueueCompletionService = reservationQueueCompletionService;
    }

    @Transactional
    public Optional<ReservationQueueEntry> process(UUID queueEntryId, UUID token) {
        return reservationQueueCompletionService.getReservationInput(queueEntryId, token)
            .map(input -> reserve(queueEntryId, input));
    }

    private ReservationQueueEntry reserve(UUID queueEntryId,
        ReservePurchaseInput input) {
        try {
            PurchaseReservation reservation = purchaseService.reserve(input);
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
