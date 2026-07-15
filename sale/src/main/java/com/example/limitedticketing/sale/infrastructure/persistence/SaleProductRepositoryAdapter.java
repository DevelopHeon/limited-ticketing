package com.example.limitedticketing.sale.infrastructure.persistence;

import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.sale.infrastructure.persistence.entity.SaleProductJpaEntity;
import com.example.limitedticketing.sale.infrastructure.persistence.repository.JpaSaleProductRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SaleProductRepositoryAdapter implements SaleProductRepository {

    private final JpaSaleProductRepository jpaSaleProductRepository;

    public SaleProductRepositoryAdapter(JpaSaleProductRepository jpaSaleProductRepository) {
        this.jpaSaleProductRepository = jpaSaleProductRepository;
    }

    @Override
    public SaleProduct save(SaleProduct saleProduct) {
        return jpaSaleProductRepository.save(SaleProductJpaEntity.from(saleProduct)).toDomain();
    }

    @Override
    public Optional<SaleProduct> findById(Long saleProductId) {
        return jpaSaleProductRepository.findById(saleProductId).map(SaleProductJpaEntity::toDomain);
    }
}
