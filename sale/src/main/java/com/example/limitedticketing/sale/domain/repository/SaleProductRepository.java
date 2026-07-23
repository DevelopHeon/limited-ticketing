package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import java.util.Optional;

public interface SaleProductRepository {

    SaleProduct save(SaleProduct saleProduct);

    Optional<SaleProduct> findById(Long saleProductId);

    SaleProductPage findPage(SaleStatus status, int page, int size, SaleProductSort sort, SortDirection direction);
}
