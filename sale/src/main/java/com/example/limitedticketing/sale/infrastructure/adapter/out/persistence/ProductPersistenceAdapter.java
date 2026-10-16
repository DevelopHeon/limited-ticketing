package com.example.limitedticketing.sale.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.infrastructure.adapter.out.persistence.entity.ProductJpaEntity;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    public ProductPersistenceAdapter(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(ProductJpaEntity.from(product)).toDomain();
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return productJpaRepository.findById(productId).map(ProductJpaEntity::toDomain);
    }
}
