package com.example.limitedticketing.sale.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.sale.application.port.out.SaleProductSearchPort;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductPage;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductSort;
import com.example.limitedticketing.sale.application.port.out.dto.SortDirection;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.sale.infrastructure.adapter.out.persistence.entity.SaleProductJpaEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class SaleProductPersistenceAdapter implements SaleProductRepository, SaleProductSearchPort {

    private final SaleProductJpaRepository saleProductJpaRepository;

    public SaleProductPersistenceAdapter(SaleProductJpaRepository saleProductJpaRepository) {
        this.saleProductJpaRepository = saleProductJpaRepository;
    }

    @Override
    public SaleProduct save(SaleProduct saleProduct) {
        return saleProductJpaRepository.save(SaleProductJpaEntity.from(saleProduct)).toDomain();
    }

    @Override
    public Optional<SaleProduct> findById(Long saleProductId) {
        return saleProductJpaRepository.findById(saleProductId).map(SaleProductJpaEntity::toDomain);
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
            ? saleProductJpaRepository.findAll(pageable)
            : saleProductJpaRepository.findByStatus(status, pageable);

        return new SaleProductPage(
            result.getContent().stream().map(SaleProductJpaEntity::toDomain).toList(),
            result.getTotalElements()
        );
    }
}
