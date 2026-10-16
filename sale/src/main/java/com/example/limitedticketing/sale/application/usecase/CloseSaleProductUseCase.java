package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.command.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;

public interface CloseSaleProductUseCase {

    SaleProductCommandResult close(CloseSaleProductCommand command);
}
