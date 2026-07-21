package com.example.limitedticketing.purchase.application.port.in;

import com.example.limitedticketing.purchase.application.command.dto.MarkPurchasePaidCommand;
import com.example.limitedticketing.purchase.application.command.service.PurchaseCommandService;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseForPaymentInput;
import com.example.limitedticketing.purchase.application.port.in.dto.MarkPurchasePaidInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentTarget;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentStatus;
import com.example.limitedticketing.purchase.application.query.dto.PurchasePaymentTargetResult;
import com.example.limitedticketing.purchase.application.query.dto.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.application.query.service.PurchaseQueryService;
import org.springframework.stereotype.Component;

@Component
public class PurchasePaymentFacade {

    private final PurchaseQueryService purchaseQueryService;
    private final PurchaseCommandService purchaseCommandService;

    public PurchasePaymentFacade(
        PurchaseQueryService purchaseQueryService,
        PurchaseCommandService purchaseCommandService
    ) {
        this.purchaseQueryService = purchaseQueryService;
        this.purchaseCommandService = purchaseCommandService;
    }

    public PurchasePaymentTarget findPaymentTarget(FindPurchaseForPaymentInput input) {
        PurchasePaymentTargetResult result = purchaseQueryService.findForPayment(
            new FindPurchaseForPaymentQuery(input.purchaseId())
        );

        return new PurchasePaymentTarget(
            result.purchaseId(),
            result.memberId(),
            result.saleProductId(),
            result.amount(),
            result.quantity(),
            result.expiresAt(),
            PurchasePaymentStatus.valueOf(result.status().name())
        );
    }

    public void markPaid(MarkPurchasePaidInput input) {
        purchaseCommandService.markPaid(new MarkPurchasePaidCommand(input.purchaseId()));
    }
}
