package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.exception.SaleException;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection fromRequestDirection(String direction) {
        try {
            return SortDirection.valueOf(direction);
        } catch (IllegalArgumentException exception) {
            throw SaleException.invalid("Unsupported sort direction");
        }
    }
}
