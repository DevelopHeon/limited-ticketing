package com.example.limitedticketing.sale.infrastructure.persistence.entity;

import com.example.limitedticketing.sale.domain.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 500)
    private String benefitDescription;

    @Column(nullable = false)
    private boolean active;

    protected ProductJpaEntity() {
    }

    private ProductJpaEntity(Product product) {
        this.id = product.getId();
        this.name = product.getName();
        this.benefitDescription = product.getBenefitDescription();
        this.active = product.isActive();
    }

    public static ProductJpaEntity from(Product product) {
        return new ProductJpaEntity(product);
    }

    public Product toDomain() {
        return Product.reconstitute(id, name, benefitDescription, active);
    }
}
