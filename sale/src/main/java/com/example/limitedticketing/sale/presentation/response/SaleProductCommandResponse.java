package com.example.limitedticketing.sale.presentation.response;

import com.example.limitedticketing.sale.application.port.in.dto.SaleProductCommandOutcome;
import com.example.limitedticketing.sale.domain.model.SaleStatus;

public record SaleProductCommandResponse(Long saleProductId, SaleStatus status) {

    public static SaleProductCommandResponse from(SaleProductCommandOutcome outcome) {
        return new SaleProductCommandResponse(outcome.saleProductId(), outcome.status());
    }
}
