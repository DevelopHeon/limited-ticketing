package com.example.limitedticketing.sale.domain.repository;

import com.example.limitedticketing.sale.domain.exception.SaleException;

public enum SaleProductSort {
    ID("id"),
    SALE_STARTS_AT("saleStartsAt"),
    SALE_PRICE("salePrice");

    private final String requestProperty;

    SaleProductSort(String requestProperty) {
        this.requestProperty = requestProperty;
    }

    public String getRequestProperty() {
        return requestProperty;
    }

    public static SaleProductSort fromRequestProperty(String requestProperty) {
        for (SaleProductSort value : values()) {
            if (value.requestProperty.equals(requestProperty)) {
                return value;
            }
        }
        throw SaleException.invalid("Unsupported sale product sort property");
    }
}
