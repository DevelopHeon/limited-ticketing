package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.sale.domain.model.SaleStatus;

public record FindSaleProductsInput(
    SaleStatus status,
    int page,
    int size,
    String sortProperty,
    String direction
) {
}
