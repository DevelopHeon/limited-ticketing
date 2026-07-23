package com.example.limitedticketing.sale.application.support;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import org.springframework.stereotype.Component;

@Component
public class SaleProductFinder {

    private final SaleProductRepository saleProductRepository;

    public SaleProductFinder(SaleProductRepository saleProductRepository) {
        this.saleProductRepository = saleProductRepository;
    }

    public SaleProduct getRequired(Long saleProductId) {
        return saleProductRepository.findById(saleProductId)
            .orElseThrow(SaleException::productNotFound);
    }
}
