package com.example.limitedticketing.sale.application.port.in.dto;

public record SaleProductCommandOutcome(Long saleProductId, String status) {

    public static SaleProductCommandOutcome of(Long saleProductId, String status) {
        return new SaleProductCommandOutcome(saleProductId, status);
    }
}
