package com.example.limitedticketing.purchase.application.usecase;

import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchasePaymentTargetResult;

public interface FindPurchaseForPaymentUseCase {

    PurchasePaymentTargetResult findForPayment(FindPurchaseForPaymentQuery query);
}
