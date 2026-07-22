package com.example.limitedticketing.purchase.domain.repository;

import com.example.limitedticketing.purchase.domain.model.Purchase;

public record IdempotentPurchase(Purchase purchase, boolean created) {
}
