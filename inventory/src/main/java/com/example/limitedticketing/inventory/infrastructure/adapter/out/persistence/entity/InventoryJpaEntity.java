package com.example.limitedticketing.inventory.infrastructure.adapter.out.persistence.entity;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "inventories",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_sale_product", columnNames = "sale_product_id")
)
public class InventoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_product_id", nullable = false, updatable = false)
    private Long saleProductId;

    @Column(nullable = false)
    private long availableQuantity;

    @Column(nullable = false)
    private long reservedQuantity;

    @Column(nullable = false)
    private long soldQuantity;

    protected InventoryJpaEntity() {
    }

    private InventoryJpaEntity(Inventory inventory) {
        this.id = inventory.getId();
        this.saleProductId = inventory.getSaleProductId();
        this.availableQuantity = inventory.getAvailableQuantity();
        this.reservedQuantity = inventory.getReservedQuantity();
        this.soldQuantity = inventory.getSoldQuantity();
    }

    public static InventoryJpaEntity from(Inventory inventory) {
        return new InventoryJpaEntity(inventory);
    }

    public Inventory toDomain() {
        return Inventory.reconstitute(id, saleProductId, availableQuantity, reservedQuantity, soldQuantity);
    }
}
