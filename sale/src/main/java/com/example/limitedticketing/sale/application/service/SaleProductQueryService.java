package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.out.SaleProductSearchPort;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductPage;
import com.example.limitedticketing.sale.application.usecase.FindPurchasableSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductDetailUseCase;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductsUseCase;
import com.example.limitedticketing.sale.application.usecase.SaleProductExistsUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindPurchasableSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.PurchasableSaleProductResult;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductDetailResult;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductPageResult;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductSummaryResult;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleProductQueryService implements
    FindPurchasableSaleProductUseCase,
    FindSaleProductDetailUseCase,
    SaleProductExistsUseCase,
    FindSaleProductsUseCase {

    private final SaleProductSearchPort saleProductSearchPort;
    private final ProductRepository productRepository;
    private final SaleProductRepository saleProductRepository;

    public SaleProductQueryService(
        SaleProductSearchPort saleProductSearchPort,
        ProductRepository productRepository,
        SaleProductRepository saleProductRepository
    ) {
        this.saleProductSearchPort = saleProductSearchPort;
        this.productRepository = productRepository;
        this.saleProductRepository = saleProductRepository;
    }

    @Transactional(readOnly = true)
    public PurchasableSaleProductResult findPurchasable(FindPurchasableSaleProductQuery query) {
        SaleProduct saleProduct = saleProductRepository.getRequiredById(query.saleProductId());

        saleProduct.ensurePurchasableAt(query.requestedAt());

        return new PurchasableSaleProductResult(saleProduct.getId(), saleProduct.getPrice());
    }

    @Transactional(readOnly = true)
    public SaleProductDetailResult findDetail(FindSaleProductQuery query) {
        SaleProduct saleProduct = saleProductRepository.getRequiredById(query.saleProductId());

        return SaleProductDetailResult.from(saleProduct, productRepository.getRequiredById(saleProduct.getProductId()));
    }

    @Transactional(readOnly = true)
    public boolean exists(FindSaleProductQuery query) {
        return saleProductRepository.findById(query.saleProductId()).isPresent();
    }

    @Transactional(readOnly = true)
    public SaleProductPageResult findPage(FindSaleProductsQuery query) {
        SaleProductPage saleProductPage = saleProductSearchPort.findPage(
            query.status(),
            query.page(),
            query.size(),
            query.sort(),
            query.direction()
        );
        List<SaleProductSummaryResult> items = saleProductPage.items().stream()
            .map(saleProduct -> SaleProductSummaryResult.from(
                saleProduct,
                productRepository.getRequiredById(saleProduct.getProductId())
            ))
            .toList();

        return new SaleProductPageResult(items, query.page(), query.size(), saleProductPage.totalCount());
    }

}
