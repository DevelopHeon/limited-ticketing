package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.command.OpenSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;

public interface OpenSaleProductUseCase {

    SaleProductCommandResult open(OpenSaleProductCommand command);
}
