package com.example.limitedticketing.sale.application.query.dto;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.domain.repository.SaleProductSort;
import com.example.limitedticketing.sale.domain.repository.SortDirection;

public record FindSaleProductsQuery(
    SaleStatus status,
    int page,
    int size,
    SaleProductSort sort,
    SortDirection direction
) {
    public FindSaleProductsQuery {
        if (page < 0 || size < 1 || size > 100) {
            throw SaleException.invalid("Page must be non-negative and size must be between 1 and 100");
        }
    }
}
