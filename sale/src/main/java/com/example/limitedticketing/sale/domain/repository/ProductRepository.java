package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.model.Product;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long productId);
}
