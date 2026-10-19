package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.web;

import com.example.limitedticketing.purchasewaiting.application.port.in.PurchaseWaitingCommandPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.PurchaseWaitingQueryPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.EnqueuePurchaseWaitingInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.FindPurchaseWaitingInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingOutput;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.web.response.PurchaseWaitingResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!worker")
@RequestMapping("/api")
public class PurchaseWaitingController {

    private final PurchaseWaitingCommandPort purchaseWaitingCommandPort;
    private final PurchaseWaitingQueryPort purchaseWaitingQueryPort;

    public PurchaseWaitingController(PurchaseWaitingCommandPort purchaseWaitingCommandPort, PurchaseWaitingQueryPort purchaseWaitingQueryPort) {
        this.purchaseWaitingCommandPort = purchaseWaitingCommandPort;
        this.purchaseWaitingQueryPort = purchaseWaitingQueryPort;
    }

    @PostMapping("/sale-products/{saleProductId}/queue-entries")
    public ResponseEntity<ApiResponse<PurchaseWaitingResponse>> enqueue(
        @PathVariable("saleProductId") Long saleProductId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId,
        @RequestHeader("Idempotency-Key") String purchaseRequestKey
    ) {
        PurchaseWaitingOutput entry = purchaseWaitingCommandPort.enqueue(
            new EnqueuePurchaseWaitingInput(memberId, saleProductId, purchaseRequestKey)
        );
        PurchaseWaitingResponse response = PurchaseWaitingResponse.from(entry);

        return ResponseEntity.accepted()
            .location(URI.create("/api/queue-entries/" + response.queueEntryId()))
            .body(ApiResponse.success(response));
    }

    @GetMapping("/queue-entries/{queueEntryId}")
    public ApiResponse<PurchaseWaitingResponse> find(
        @PathVariable("queueEntryId") UUID queueEntryId,
        @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        PurchaseWaitingOutput entry = purchaseWaitingQueryPort.find(
            new FindPurchaseWaitingInput(queueEntryId, memberId)
        );
        return ApiResponse.success(PurchaseWaitingResponse.from(entry));
    }
}
