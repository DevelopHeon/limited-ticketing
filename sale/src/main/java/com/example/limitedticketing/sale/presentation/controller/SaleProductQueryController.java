package com.example.limitedticketing.sale.presentation.controller;

import com.example.limitedticketing.sale.application.port.in.SaleProductQueryFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductsInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductDetail;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductPage;
import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.presentation.response.SaleProductDetailResponse;
import com.example.limitedticketing.sale.presentation.response.SaleProductPageResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sale-products")
public class SaleProductQueryController {

    private final SaleProductQueryFacade saleProductQueryFacade;

    public SaleProductQueryController(SaleProductQueryFacade saleProductQueryFacade) {
        this.saleProductQueryFacade = saleProductQueryFacade;
    }

    @GetMapping
    public ApiResponse<SaleProductPageResponse> findPage(
        @RequestParam(name = "status", required = false) SaleStatus status,
        @PageableDefault(size = 20, sort = "saleStartsAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Sort.Order order = pageable.getSort().stream()
            .findFirst()
            .orElseThrow(() -> SaleException.invalid("A sort property is required"));
        SaleProductPage saleProductPage = saleProductQueryFacade.findPage(
            new FindSaleProductsInput(
                status,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                order.getProperty(),
                order.getDirection().name()
            )
        );
        return ApiResponse.success(SaleProductPageResponse.from(saleProductPage));
    }

    @GetMapping("/{saleProductId}")
    public ApiResponse<SaleProductDetailResponse> findDetail(@PathVariable("saleProductId") Long saleProductId) {
        SaleProductDetail detail = saleProductQueryFacade.findDetail(new FindSaleProductInput(saleProductId));
        return ApiResponse.success(SaleProductDetailResponse.from(detail));
    }
}
