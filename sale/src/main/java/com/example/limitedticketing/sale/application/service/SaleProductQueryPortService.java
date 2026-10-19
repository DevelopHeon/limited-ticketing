package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.in.SaleProductQueryPort;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductsInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductDetail;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductPage;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductSummary;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductSort;
import com.example.limitedticketing.sale.application.port.out.dto.SortDirection;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductDetailUseCase;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductsUseCase;
import com.example.limitedticketing.sale.application.usecase.SaleProductExistsUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductDetailResult;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductPageResult;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import org.springframework.stereotype.Service;

@Service
public class SaleProductQueryPortService implements SaleProductQueryPort {

    private final FindSaleProductDetailUseCase findSaleProductDetailUseCase;
    private final SaleProductExistsUseCase saleProductExistsUseCase;
    private final FindSaleProductsUseCase findSaleProductsUseCase;

    public SaleProductQueryPortService(
        FindSaleProductDetailUseCase findSaleProductDetailUseCase,
        SaleProductExistsUseCase saleProductExistsUseCase,
        FindSaleProductsUseCase findSaleProductsUseCase
    ) {
        this.findSaleProductDetailUseCase = findSaleProductDetailUseCase;
        this.saleProductExistsUseCase = saleProductExistsUseCase;
        this.findSaleProductsUseCase = findSaleProductsUseCase;
    }

    public SaleProductDetail findDetail(FindSaleProductInput input) {
        SaleProductDetailResult result = findSaleProductDetailUseCase.findDetail(
            new FindSaleProductQuery(input.saleProductId())
        );
        return SaleProductDetail.of(
            result.saleProductId(),
            result.productName(),
            result.benefitDescription(),
            result.salePrice(),
            result.saleStartsAt(),
            result.saleEndsAt(),
            result.status().name()
        );
    }

    public boolean isClosed(FindSaleProductInput input) {
        return findSaleProductDetailUseCase.findDetail(new FindSaleProductQuery(input.saleProductId())).status()
            == SaleStatus.CLOSED;
    }

    public boolean exists(FindSaleProductInput input) {
        return saleProductExistsUseCase.exists(new FindSaleProductQuery(input.saleProductId()));
    }

    public SaleProductPage findPage(FindSaleProductsInput input) {
        SaleProductPageResult result = findSaleProductsUseCase.findPage(
            new FindSaleProductsQuery(
                input.status() == null ? null : SaleStatus.fromRequestValue(input.status()),
                input.page(),
                input.size(),
                SaleProductSort.fromRequestProperty(input.sortProperty()),
                SortDirection.fromRequestDirection(input.direction())
            )
        );
        return SaleProductPage.of(
            result.items().stream()
                .map(item -> SaleProductSummary.of(
                    item.saleProductId(),
                    item.productName(),
                    item.salePrice(),
                    item.saleStartsAt(),
                    item.saleEndsAt(),
                    item.status().name()
                ))
                .toList(),
            result.page(),
            result.size(),
            result.totalCount()
        );
    }
}
