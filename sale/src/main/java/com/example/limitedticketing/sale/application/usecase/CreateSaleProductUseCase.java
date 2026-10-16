package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.command.CreateSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;

public interface CreateSaleProductUseCase {

    SaleProductCommandResult create(CreateSaleProductCommand command);
}
