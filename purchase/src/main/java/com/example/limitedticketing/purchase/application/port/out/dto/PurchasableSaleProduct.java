package com.example.limitedticketing.purchase.application.port.out.dto;

import com.example.limitedticketing.shared.vo.Money;

public record PurchasableSaleProduct(Long saleProductId, Money price) {
}
