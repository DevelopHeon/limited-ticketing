package com.example.limitedticketing.sale.domain.model;

import com.example.limitedticketing.sale.domain.exception.SaleException;
public class Product {

    private final Long id;
    private final String name;
    private final String benefitDescription;
    private final boolean active;

    private Product(Long id, String name, String benefitDescription, boolean active) {
        this.id = id;
        this.name = requireText(name, "Product name");
        this.benefitDescription = requireText(benefitDescription, "Benefit description");
        this.active = active;
    }

    public static Product create(String name, String benefitDescription) {
        return new Product(null, name, benefitDescription, true);
    }

    public static Product reconstitute(Long id, String name, String benefitDescription, boolean active) {
        return new Product(id, name, benefitDescription, active);
    }

    public void ensureActive() {
        if (!active) {
            throw SaleException.productInactive();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBenefitDescription() {
        return benefitDescription;
    }

    public boolean isActive() {
        return active;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw SaleException.invalid(fieldName + " is required");
        }
        return value;
    }
}
