package com.example.limitedticketing.sale.application.port.out.dto;

import com.example.limitedticketing.sale.domain.model.SaleStatus;

public record SaleProductCacheQuery(SaleStatus status, int page, int size, SaleProductSort sort, SortDirection direction) {
}
