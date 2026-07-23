package com.example.limitedticketing.sale.infrastructure.persistence;

import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.domain.repository.SaleProductPage;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductSort;
import com.example.limitedticketing.sale.domain.repository.SortDirection;
import com.example.limitedticketing.sale.infrastructure.persistence.entity.SaleProductJpaEntity;
import com.example.limitedticketing.sale.infrastructure.persistence.repository.JpaSaleProductRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Override
    public SaleProductPage findPage(
        SaleStatus status,
        int page,
        int size,
        SaleProductSort sort,
        SortDirection direction
    ) {
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.valueOf(direction.name()), sort.getRequestProperty())
        );
        Page<SaleProductJpaEntity> result = status == null
            ? jpaSaleProductRepository.findAll(pageable)
            : jpaSaleProductRepository.findByStatus(status, pageable);

        return new SaleProductPage(
            result.getContent().stream().map(SaleProductJpaEntity::toDomain).toList(),
            result.getTotalElements()
        );
    }
}
