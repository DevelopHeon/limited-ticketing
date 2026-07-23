package com.example.limitedticketing.sale.application.command.dto;

import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;

public record SaleProductCommandResult(Long saleProductId, SaleStatus status) {

    public static SaleProductCommandResult from(SaleProduct saleProduct) {
        return new SaleProductCommandResult(saleProduct.getId(), saleProduct.getStatus());
    }
}
