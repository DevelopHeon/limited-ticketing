package com.example.limitedticketing.sale.application.port.in;

import com.example.limitedticketing.sale.application.command.dto.ChangeSalePeriodCommand;
import com.example.limitedticketing.sale.application.command.dto.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.command.dto.CreateSaleProductCommand;
import com.example.limitedticketing.sale.application.command.dto.OpenSaleProductCommand;
import com.example.limitedticketing.sale.application.command.dto.SaleProductCommandResult;
import com.example.limitedticketing.sale.application.command.service.SaleProductCommandService;
import com.example.limitedticketing.sale.application.port.in.dto.ChangeSalePeriodInput;
import com.example.limitedticketing.sale.application.port.in.dto.CloseSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.CreateSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.OpenSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductCommandOutcome;
import org.springframework.stereotype.Component;

@Component
public class SaleProductAdminFacade {

    private final SaleProductCommandService saleProductCommandService;

    public SaleProductAdminFacade(SaleProductCommandService saleProductCommandService) {
        this.saleProductCommandService = saleProductCommandService;
    }

    public SaleProductCommandOutcome create(CreateSaleProductInput input) {
        SaleProductCommandResult result = saleProductCommandService.create(new CreateSaleProductCommand(
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
        SaleProductCommandResult result = saleProductCommandService.changeSalePeriod(
            new ChangeSalePeriodCommand(input.saleProductId(), input.saleStartsAt(), input.saleEndsAt())
        );
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }

    public SaleProductCommandOutcome open(OpenSaleProductInput input) {
        SaleProductCommandResult result = saleProductCommandService.open(
            new OpenSaleProductCommand(input.saleProductId())
        );
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }

    public SaleProductCommandOutcome close(CloseSaleProductInput input) {
        SaleProductCommandResult result = saleProductCommandService.close(
            new CloseSaleProductCommand(input.saleProductId())
        );
        return SaleProductCommandOutcome.of(result.saleProductId(), result.status().name());
    }
}
