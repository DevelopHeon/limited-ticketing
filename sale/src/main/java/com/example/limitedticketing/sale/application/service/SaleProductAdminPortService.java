package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.in.SaleProductAdminPort;
import com.example.limitedticketing.sale.application.port.in.dto.ChangeSalePeriodInput;
import com.example.limitedticketing.sale.application.port.in.dto.CloseSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.CreateSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.OpenSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductCommandOutcome;
import com.example.limitedticketing.sale.application.usecase.ChangeSalePeriodUseCase;
import com.example.limitedticketing.sale.application.usecase.CloseSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.CreateSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.OpenSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.command.ChangeSalePeriodCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.CreateSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.command.OpenSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;
import org.springframework.stereotype.Service;

@Service
public class SaleProductAdminPortService implements SaleProductAdminPort {

    private final CreateSaleProductUseCase createSaleProductUseCase;
    private final ChangeSalePeriodUseCase changeSalePeriodUseCase;
    private final OpenSaleProductUseCase openSaleProductUseCase;
    private final CloseSaleProductUseCase closeSaleProductUseCase;

    public SaleProductAdminPortService(
        CreateSaleProductUseCase createSaleProductUseCase,
        ChangeSalePeriodUseCase changeSalePeriodUseCase,
        OpenSaleProductUseCase openSaleProductUseCase,
        CloseSaleProductUseCase closeSaleProductUseCase
    ) {
        this.createSaleProductUseCase = createSaleProductUseCase;
        this.changeSalePeriodUseCase = changeSalePeriodUseCase;
        this.openSaleProductUseCase = openSaleProductUseCase;
        this.closeSaleProductUseCase = closeSaleProductUseCase;
    }

    public SaleProductCommandOutcome create(CreateSaleProductInput input) {
        SaleProductCommandResult result = createSaleProductUseCase.create(new CreateSaleProductCommand(
            input.productName(),
            input.benefitDescription(),
            input.salePrice(),
            input.saleStartsAt(),
            input.saleEndsAt(),
            input.initialInventoryQuantity()
        ));
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }

    public SaleProductCommandOutcome changeSalePeriod(ChangeSalePeriodInput input) {
        SaleProductCommandResult result = changeSalePeriodUseCase.changeSalePeriod(
            new ChangeSalePeriodCommand(input.saleProductId(), input.saleStartsAt(), input.saleEndsAt())
        );
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }

    public SaleProductCommandOutcome open(OpenSaleProductInput input) {
        SaleProductCommandResult result = openSaleProductUseCase.open(
            new OpenSaleProductCommand(input.saleProductId())
        );
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }

    public SaleProductCommandOutcome close(CloseSaleProductInput input) {
        SaleProductCommandResult result = closeSaleProductUseCase.close(
            new CloseSaleProductCommand(input.saleProductId())
        );
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }
}
