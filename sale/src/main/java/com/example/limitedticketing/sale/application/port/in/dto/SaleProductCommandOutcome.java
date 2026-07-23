package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.sale.application.command.dto.SaleProductCommandResult;
import com.example.limitedticketing.sale.domain.model.SaleStatus;

public record SaleProductCommandOutcome(Long saleProductId, SaleStatus status) {

    public static SaleProductCommandOutcome from(SaleProductCommandResult result) {
        return new SaleProductCommandOutcome(result.saleProductId(), result.status());
    }
}
