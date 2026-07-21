package com.example.limitedticketing.purchase.presentation.controller;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.CancelPurchaseInput;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseLifecycleController {

    private final PurchaseCommandFacade purchaseCommandFacade;

    public PurchaseLifecycleController(PurchaseCommandFacade purchaseCommandFacade) {
        this.purchaseCommandFacade = purchaseCommandFacade;
    }

    @PostMapping("/{purchaseId}/cancel")
    public ResponseEntity<Void> cancel(
        @PathVariable("purchaseId") Long purchaseId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        purchaseCommandFacade.cancel(new CancelPurchaseInput(purchaseId, memberId));

        return ResponseEntity.noContent().build();
    }
}
