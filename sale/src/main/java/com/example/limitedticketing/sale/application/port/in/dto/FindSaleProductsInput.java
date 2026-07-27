package com.example.limitedticketing.sale.application.port.in.dto;

public record FindSaleProductsInput(
    String status,
    int page,
    int size,
    String sortProperty,
    String direction
) {
}
