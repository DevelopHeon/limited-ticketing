package com.example.limitedticketing.sale.application.query.service;

import com.example.limitedticketing.sale.application.query.dto.FindPurchasableSaleProductQuery;
import com.example.limitedticketing.sale.application.query.dto.PurchasableSaleProductResult;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class SaleProductQueryService {

    private final SaleProductRepository saleProductRepository;

    public SaleProductQueryService(SaleProductRepository saleProductRepository) {
        this.saleProductRepository = saleProductRepository;
    }

    public PurchasableSaleProductResult findPurchasable(FindPurchasableSaleProductQuery query) {
        SaleProduct saleProduct = saleProductRepository.findById(query.saleProductId())
            .orElseThrow(() -> new NoSuchElementException("Sale product not found"));

        saleProduct.ensurePurchasableAt(query.requestedAt());

        return new PurchasableSaleProductResult(saleProduct.getId(), saleProduct.getPrice());
    }
}
