package com.example.limitedticketing.sale.application.port.in;

import com.example.limitedticketing.sale.application.port.in.dto.ChangeSalePeriodInput;
import com.example.limitedticketing.sale.application.port.in.dto.CloseSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.CreateSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.OpenSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductCommandOutcome;

public interface SaleProductAdminPort {

    SaleProductCommandOutcome create(CreateSaleProductInput input);

    SaleProductCommandOutcome changeSalePeriod(ChangeSalePeriodInput input);

    SaleProductCommandOutcome open(OpenSaleProductInput input);

    SaleProductCommandOutcome close(CloseSaleProductInput input);
}
