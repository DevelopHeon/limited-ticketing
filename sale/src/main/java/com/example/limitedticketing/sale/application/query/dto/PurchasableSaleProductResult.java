package com.example.limitedticketing.sale.application.query.dto;

import com.example.limitedticketing.shared.vo.Money;

public record PurchasableSaleProductResult(Long saleProductId, Money price) {
}
