package com.example.limitedticketing.purchase.infrastructure.adapter.in.web;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandPort;
import com.example.limitedticketing.purchase.application.port.in.dto.CancelPurchaseInput;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!worker")
@RequestMapping("/api/purchases")
public class PurchaseLifecycleController {

    private final PurchaseCommandPort purchaseCommandPort;

    public PurchaseLifecycleController(PurchaseCommandPort purchaseCommandPort) {
        this.purchaseCommandPort = purchaseCommandPort;
    }

    @PostMapping("/{purchaseId}/cancel")
    public ResponseEntity<Void> cancel(
        @PathVariable("purchaseId") Long purchaseId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        purchaseCommandPort.cancel(new CancelPurchaseInput(purchaseId, memberId));

        return ResponseEntity.noContent().build();
    }
}
