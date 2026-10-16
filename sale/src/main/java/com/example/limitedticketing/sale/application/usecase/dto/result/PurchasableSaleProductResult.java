package com.example.limitedticketing.sale.application.usecase.dto.result;

import com.example.limitedticketing.shared.vo.Money;

public record PurchasableSaleProductResult(Long saleProductId, Money price) {
}
