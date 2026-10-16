package com.example.limitedticketing.sale.application.port.in;

import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.FindSaleProductsInput;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductDetail;
import com.example.limitedticketing.sale.application.port.in.dto.SaleProductPage;

public interface SaleProductQueryPort {

    SaleProductDetail findDetail(FindSaleProductInput input);

    boolean isClosed(FindSaleProductInput input);

    boolean exists(FindSaleProductInput input);

    SaleProductPage findPage(FindSaleProductsInput input);
}
