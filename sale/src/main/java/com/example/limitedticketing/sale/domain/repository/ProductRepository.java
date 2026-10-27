package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.Product;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long productId);

    default Product getRequiredById(Long productId) {
        return findById(productId).orElseThrow(SaleException::productNotFound);
    }
}
