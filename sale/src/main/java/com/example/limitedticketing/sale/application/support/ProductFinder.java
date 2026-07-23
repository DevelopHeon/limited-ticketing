package com.example.limitedticketing.sale.application.support;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import org.springframework.stereotype.Component;

@Component
public class ProductFinder {

    private final ProductRepository productRepository;

    public ProductFinder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product getRequired(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(SaleException::productNotFound);
    }
}
