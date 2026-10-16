package com.example.limitedticketing.sale.application.usecase;

import com.example.limitedticketing.sale.application.usecase.dto.command.ChangeSalePeriodCommand;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductCommandResult;

public interface ChangeSalePeriodUseCase {

    SaleProductCommandResult changeSalePeriod(ChangeSalePeriodCommand command);
}
