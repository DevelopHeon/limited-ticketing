package com.example.limitedticketing.sale.infrastructure.persistence;

import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.infrastructure.persistence.entity.ProductJpaEntity;
import com.example.limitedticketing.sale.infrastructure.persistence.repository.JpaProductRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepositoryAdapter implements ProductRepository {

    private final JpaProductRepository jpaProductRepository;

    public ProductRepositoryAdapter(JpaProductRepository jpaProductRepository) {
        this.jpaProductRepository = jpaProductRepository;
    }

    @Override
    public Product save(Product product) {
        return jpaProductRepository.save(ProductJpaEntity.from(product)).toDomain();
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return jpaProductRepository.findById(productId).map(ProductJpaEntity::toDomain);
    }
}
