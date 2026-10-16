package com.example.limitedticketing.sale.infrastructure.adapter.in.web.response;

import com.example.limitedticketing.sale.application.port.in.dto.SaleProductCommandOutcome;

public record SaleProductCommandResponse(Long saleProductId, String status) {

    public static SaleProductCommandResponse from(SaleProductCommandOutcome outcome) {
        return new SaleProductCommandResponse(outcome.saleProductId(), outcome.status());
    }
}
