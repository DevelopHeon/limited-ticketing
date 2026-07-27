package com.example.limitedticketing.sale.domain.model;

import com.example.limitedticketing.sale.domain.exception.SaleException;

public enum SaleStatus {
    DRAFT,
    OPEN,
    CLOSED;

    public static SaleStatus fromRequestValue(String value) {
        try {
            return SaleStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw SaleException.invalid("Unsupported sale status");
        }
    }
}
