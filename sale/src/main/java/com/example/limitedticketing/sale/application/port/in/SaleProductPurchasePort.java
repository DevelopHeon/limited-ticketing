package com.example.limitedticketing.sale.application.port.in;

import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.PurchasableSaleProduct;

public interface SaleProductPurchasePort {

    PurchasableSaleProduct findPurchasable(FindPurchasableSaleProductInput input);
}
