package com.example.limitedticketing.purchase.presentation.controller;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchase.presentation.response.PurchaseReservationResponse;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sale-products")
public class PurchaseController {

    private final PurchaseCommandFacade purchaseCommandFacade;

    public PurchaseController(PurchaseCommandFacade purchaseCommandFacade) {
        this.purchaseCommandFacade = purchaseCommandFacade;
    }

    @PostMapping("/{saleProductId}/purchases")
    public ResponseEntity<PurchaseReservationResponse> reserve(
        @PathVariable("saleProductId") Long saleProductId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        PurchaseReservation reservation = purchaseCommandFacade.reserve(
            new ReservePurchaseInput(memberId, saleProductId)
        );
        PurchaseReservationResponse response = new PurchaseReservationResponse(
            reservation.purchaseId(),
            reservation.status(),
            reservation.expiresAt()
        );

        return ResponseEntity.created(URI.create("/api/purchases/" + response.purchaseId())).body(response);
    }
}
