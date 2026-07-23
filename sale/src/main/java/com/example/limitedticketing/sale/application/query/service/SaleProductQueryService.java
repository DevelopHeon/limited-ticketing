package com.example.limitedticketing.sale.application.query.service;

import com.example.limitedticketing.sale.application.query.dto.FindPurchasableSaleProductQuery;
import com.example.limitedticketing.sale.application.query.dto.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.query.dto.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.query.dto.PurchasableSaleProductResult;
import com.example.limitedticketing.sale.application.query.dto.SaleProductDetailResult;
import com.example.limitedticketing.sale.application.query.dto.SaleProductPageResult;
import com.example.limitedticketing.sale.application.query.dto.SaleProductSummaryResult;
import com.example.limitedticketing.sale.application.support.ProductFinder;
import com.example.limitedticketing.sale.application.support.SaleProductFinder;
import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.SaleProductPage;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleProductQueryService {

    private final SaleProductRepository saleProductRepository;
    private final SaleProductFinder saleProductFinder;
    private final ProductFinder productFinder;

    public SaleProductQueryService(
        SaleProductRepository saleProductRepository,
        SaleProductFinder saleProductFinder,
        ProductFinder productFinder
    ) {
        this.saleProductRepository = saleProductRepository;
        this.saleProductFinder = saleProductFinder;
        this.productFinder = productFinder;
    }

    @Transactional(readOnly = true)
    public PurchasableSaleProductResult findPurchasable(FindPurchasableSaleProductQuery query) {
        SaleProduct saleProduct = saleProductFinder.getRequired(query.saleProductId());

        saleProduct.ensurePurchasableAt(query.requestedAt());

        return new PurchasableSaleProductResult(saleProduct.getId(), saleProduct.getPrice());
    }

    @Transactional(readOnly = true)
    public SaleProductDetailResult findDetail(FindSaleProductQuery query) {
        SaleProduct saleProduct = saleProductFinder.getRequired(query.saleProductId());

        return SaleProductDetailResult.from(saleProduct, productFinder.getRequired(saleProduct.getProductId()));
    }

    @Transactional(readOnly = true)
    public SaleProductPageResult findPage(FindSaleProductsQuery query) {
        SaleProductPage saleProductPage = saleProductRepository.findPage(
            query.status(),
            query.page(),
            query.size(),
            query.sort(),
            query.direction()
        );
        List<SaleProductSummaryResult> items = saleProductPage.items().stream()
            .map(saleProduct -> SaleProductSummaryResult.from(
                saleProduct,
                productFinder.getRequired(saleProduct.getProductId())
            ))
            .toList();

        return new SaleProductPageResult(items, query.page(), query.size(), saleProductPage.totalCount());
    }
}
