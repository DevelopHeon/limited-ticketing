package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import java.util.Optional;

public interface SaleProductRepository {

    SaleProduct save(SaleProduct saleProduct);

    Optional<SaleProduct> findById(Long saleProductId);

    default SaleProduct getRequiredById(Long saleProductId) {
        return findById(saleProductId).orElseThrow(SaleException::productNotFound);
    }

}
