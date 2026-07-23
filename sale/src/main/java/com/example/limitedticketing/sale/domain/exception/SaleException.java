package com.example.limitedticketing.sale.domain.exception;

import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;

public final class SaleException extends DomainException {

    private SaleException(DomainErrorType errorType, String code, String message) {
        super(errorType, code, message);
    }

    public static SaleException productNotFound() {
        return new SaleException(DomainErrorType.NOT_FOUND, "SALE_PRODUCT_NOT_FOUND", "Sale product not found");
    }

    public static SaleException invalid(String message) {
        return new SaleException(DomainErrorType.INVALID_REQUEST, "INVALID_SALE", message);
    }

    public static SaleException unavailable() {
        return new SaleException(DomainErrorType.CONFLICT, "SALE_UNAVAILABLE", "Sale product is not available");
    }

    public static SaleException invalidState(String message) {
        return new SaleException(DomainErrorType.CONFLICT, "INVALID_SALE_STATE", message);
    }

    public static SaleException outsideSalePeriod() {
        return new SaleException(
            DomainErrorType.CONFLICT,
            "SALE_OUTSIDE_PERIOD",
            "Sale can only open during the configured sale period"
        );
    }

    public static SaleException productInactive() {
        return new SaleException(DomainErrorType.CONFLICT, "PRODUCT_INACTIVE", "Product is inactive");
    }
}
