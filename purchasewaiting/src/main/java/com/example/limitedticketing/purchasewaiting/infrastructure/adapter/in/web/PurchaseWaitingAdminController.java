package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.web;

import com.example.limitedticketing.purchasewaiting.application.port.in.PurchaseWaitingReplayPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.dto.PurchaseWaitingReplayOutput;
import com.example.limitedticketing.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!worker")
public class PurchaseWaitingAdminController {
    private final PurchaseWaitingReplayPort purchaseWaitingReplayPort;

    public PurchaseWaitingAdminController(PurchaseWaitingReplayPort purchaseWaitingReplayPort) {
        this.purchaseWaitingReplayPort = purchaseWaitingReplayPort;
    }

    @PostMapping("/api/admin/queue-entries/{queueEntryId}/replay")
    public ApiResponse<PurchaseWaitingReplayOutput> replay(@PathVariable("queueEntryId") UUID queueEntryId) {
        return ApiResponse.success(purchaseWaitingReplayPort.replay(queueEntryId));
    }
}
