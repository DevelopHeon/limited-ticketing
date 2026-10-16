package com.example.limitedticketing.sale.application.port.out;

import com.example.limitedticketing.sale.application.port.out.dto.SaleProductPage;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductSort;
import com.example.limitedticketing.sale.application.port.out.dto.SortDirection;
import com.example.limitedticketing.sale.domain.model.SaleStatus;

public interface SaleProductSearchPort {

    SaleProductPage findPage(SaleStatus status, int page, int size, SaleProductSort sort, SortDirection direction);
}
