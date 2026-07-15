package com.example.limitedticketing.sale.application.port.in.dto;

import com.example.limitedticketing.shared.vo.Money;

public record PurchasableSaleProduct(Long saleProductId, Money price) {
}
