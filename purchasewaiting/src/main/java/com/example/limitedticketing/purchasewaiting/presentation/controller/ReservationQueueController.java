package com.example.limitedticketing.purchasewaiting.presentation.controller;

import com.example.limitedticketing.purchasewaiting.application.port.in.ReservationQueueFacade;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.EnqueueReservationInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.FindReservationQueueEntryInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.presentation.response.ReservationQueueEntryResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReservationQueueController {

    private final ReservationQueueFacade reservationQueueFacade;

    public ReservationQueueController(ReservationQueueFacade reservationQueueFacade) {
        this.reservationQueueFacade = reservationQueueFacade;
    }

    @PostMapping("/sale-products/{saleProductId}/queue-entries")
    public ResponseEntity<ApiResponse<ReservationQueueEntryResponse>> enqueue(
        @PathVariable("saleProductId") Long saleProductId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId,
        @RequestHeader("Idempotency-Key") String purchaseRequestKey
    ) {
        ReservationQueueEntry entry = reservationQueueFacade.enqueue(
            new EnqueueReservationInput(memberId, saleProductId, purchaseRequestKey)
        );
        ReservationQueueEntryResponse response = ReservationQueueEntryResponse.from(entry);

        return ResponseEntity.accepted()
            .location(URI.create("/api/queue-entries/" + response.queueEntryId()))
            .body(ApiResponse.success(response));
    }

    @GetMapping("/queue-entries/{queueEntryId}")
    public ApiResponse<ReservationQueueEntryResponse> find(
        @PathVariable("queueEntryId") UUID queueEntryId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        ReservationQueueEntry entry = reservationQueueFacade.find(
            new FindReservationQueueEntryInput(queueEntryId, memberId)
        );
        return ApiResponse.success(ReservationQueueEntryResponse.from(entry));
    }
}
