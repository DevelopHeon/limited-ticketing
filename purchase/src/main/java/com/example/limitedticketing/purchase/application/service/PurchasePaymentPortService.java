package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.port.in.PurchasePaymentPort;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseForPaymentInput;
import com.example.limitedticketing.purchase.application.port.in.dto.MarkPurchasePaidInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentStatus;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentTarget;
import com.example.limitedticketing.purchase.application.usecase.FindPurchaseForPaymentUseCase;
import com.example.limitedticketing.purchase.application.usecase.MarkPurchasePaidUseCase;
import com.example.limitedticketing.purchase.application.usecase.dto.command.MarkPurchasePaidCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchasePaymentTargetResult;
import org.springframework.stereotype.Service;

@Service
public class PurchasePaymentPortService implements PurchasePaymentPort {

    private final FindPurchaseForPaymentUseCase findPurchaseForPaymentUseCase;
    private final MarkPurchasePaidUseCase markPurchasePaidUseCase;

    public PurchasePaymentPortService(
        FindPurchaseForPaymentUseCase findPurchaseForPaymentUseCase,
        MarkPurchasePaidUseCase markPurchasePaidUseCase
    ) {
        this.findPurchaseForPaymentUseCase = findPurchaseForPaymentUseCase;
        this.markPurchasePaidUseCase = markPurchasePaidUseCase;
    }

    public PurchasePaymentTarget findPaymentTarget(FindPurchaseForPaymentInput input) {
        PurchasePaymentTargetResult result = findPurchaseForPaymentUseCase.findForPayment(
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
        markPurchasePaidUseCase.markPaid(new MarkPurchasePaidCommand(input.purchaseId()));
    }
}
