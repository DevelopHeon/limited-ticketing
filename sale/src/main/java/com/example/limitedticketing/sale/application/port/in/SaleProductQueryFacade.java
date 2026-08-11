package com.example.limitedticketing.sale.application.port.in;

import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductsInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductDetail;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductPage;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductSummary;
import com.example.limitedticketing.sale.application.query.dto.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.query.dto.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.query.dto.SaleProductDetailResult;
import com.example.limitedticketing.sale.application.query.dto.SaleProductPageResult;
import com.example.limitedticketing.sale.application.query.service.SaleProductQueryService;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.domain.repository.SaleProductSort;
import com.example.limitedticketing.sale.domain.repository.SortDirection;
import org.springframework.stereotype.Component;

@Component
public class SaleProductQueryFacade {

    private final SaleProductQueryService saleProductQueryService;

    public SaleProductQueryFacade(SaleProductQueryService saleProductQueryService) {
        this.saleProductQueryService = saleProductQueryService;
    }

    public SaleProductDetail findDetail(FindSaleProductInput input) {
        SaleProductDetailResult result = saleProductQueryService.findDetail(
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
        return saleProductQueryService.findDetail(new FindSaleProductQuery(input.saleProductId())).status()
            == SaleStatus.CLOSED;
    }

    public boolean exists(FindSaleProductInput input) {
        return saleProductQueryService.exists(new FindSaleProductQuery(input.saleProductId()));
    }

    public SaleProductPage findPage(FindSaleProductsInput input) {
        SaleProductPageResult result = saleProductQueryService.findPage(
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
