package com.example.limitedticketing.sale.infrastructure.adapter.in.web;

import com.example.limitedticketing.sale.application.port.in.SaleProductQueryPort;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductsInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductDetail;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductPage;
import com.example.limitedticketing.sale.infrastructure.adapter.in.web.response.SaleProductDetailResponse;
import com.example.limitedticketing.sale.infrastructure.adapter.in.web.response.SaleProductPageResponse;
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

    private final SaleProductQueryPort saleProductQueryPort;

    public SaleProductQueryController(SaleProductQueryPort saleProductQueryPort) {
        this.saleProductQueryPort = saleProductQueryPort;
    }

    @GetMapping
    public ApiResponse<SaleProductPageResponse> findPage(
        @RequestParam(name = "status", required = false) String status,
        @PageableDefault(size = 20, sort = "saleStartsAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Sort.Order order = pageable.getSort().stream()
            .findFirst()
            .orElse(new Sort.Order(Sort.Direction.ASC, "saleStartsAt"));
        SaleProductPage saleProductPage = saleProductQueryPort.findPage(
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
        SaleProductDetail detail = saleProductQueryPort.findDetail(new FindSaleProductInput(saleProductId));
        return ApiResponse.success(SaleProductDetailResponse.from(detail));
    }
}
