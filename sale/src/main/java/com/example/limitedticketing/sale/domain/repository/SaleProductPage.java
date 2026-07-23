package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.model.SaleProduct;
import java.util.List;

public record SaleProductPage(List<SaleProduct> items, long totalCount) {
}
