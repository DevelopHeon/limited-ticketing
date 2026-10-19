package com.example.limitedticketing.sale.infrastructure.adapter.in.web;

import com.example.limitedticketing.sale.application.port.in.SaleProductAdminPort;
import com.example.limitedticketing.sale.application.port.in.dto.ChangeSalePeriodInput;
import com.example.limitedticketing.sale.application.port.in.dto.CloseSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.CreateSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.OpenSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductCommandOutcome;
import com.example.limitedticketing.sale.infrastructure.adapter.in.web.request.ChangeSalePeriodRequest;
import com.example.limitedticketing.sale.infrastructure.adapter.in.web.request.CreateSaleProductRequest;
import com.example.limitedticketing.sale.infrastructure.adapter.in.web.response.SaleProductCommandResponse;
import com.example.limitedticketing.shared.api.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!worker")
@RequestMapping("/api/admin/sale-products")
public class SaleProductAdminController {

    private final SaleProductAdminPort saleProductAdminPort;

    public SaleProductAdminController(SaleProductAdminPort saleProductAdminPort) {
        this.saleProductAdminPort = saleProductAdminPort;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaleProductCommandResponse>> create(
        @Valid @RequestBody CreateSaleProductRequest request
    ) {
        SaleProductCommandOutcome outcome = saleProductAdminPort.create(new CreateSaleProductInput(
            request.productName(),
            request.benefitDescription(),
            request.salePrice(),
            request.saleStartsAt(),
            request.saleEndsAt(),
            request.initialInventoryQuantity()
        ));
        SaleProductCommandResponse response = SaleProductCommandResponse.from(outcome);

        return ResponseEntity.created(URI.create("/api/sale-products/" + response.saleProductId()))
            .body(ApiResponse.success(response));
    }

    @PutMapping("/{saleProductId}/sale-period")
    public ApiResponse<SaleProductCommandResponse> changeSalePeriod(
        @PathVariable("saleProductId") Long saleProductId,
        @Valid @RequestBody ChangeSalePeriodRequest request
    ) {
        SaleProductCommandOutcome outcome = saleProductAdminPort.changeSalePeriod(
            new ChangeSalePeriodInput(saleProductId, request.saleStartsAt(), request.saleEndsAt())
        );
        return ApiResponse.success(SaleProductCommandResponse.from(outcome));
    }

    @PostMapping("/{saleProductId}/open")
    public ApiResponse<SaleProductCommandResponse> open(@PathVariable("saleProductId") Long saleProductId) {
        SaleProductCommandOutcome outcome = saleProductAdminPort.open(new OpenSaleProductInput(saleProductId));
        return ApiResponse.success(SaleProductCommandResponse.from(outcome));
    }

    @PostMapping("/{saleProductId}/close")
    public ApiResponse<SaleProductCommandResponse> close(@PathVariable("saleProductId") Long saleProductId) {
        SaleProductCommandOutcome outcome = saleProductAdminPort.close(new CloseSaleProductInput(saleProductId));
        return ApiResponse.success(SaleProductCommandResponse.from(outcome));
    }
}
