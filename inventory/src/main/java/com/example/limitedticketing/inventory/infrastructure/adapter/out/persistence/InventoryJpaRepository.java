package com.example.limitedticketing.inventory.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.inventory.infrastructure.adapter.out.persistence.entity.InventoryJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {

    Optional<InventoryJpaEntity> findBySaleProductId(Long saleProductId);

    @Modifying
    @Query(value = """
        update inventories
        set available_quantity = available_quantity - :quantity,
            reserved_quantity = reserved_quantity + :quantity
        where sale_product_id = :saleProductId
          and available_quantity >= :quantity
        """, nativeQuery = true)
    int reserveIfAvailable(
        @Param("saleProductId") Long saleProductId,
        @Param("quantity") long quantity
    );

    @Modifying
    @Query(value = """
        update inventories
        set reserved_quantity = reserved_quantity - :quantity,
            sold_quantity = sold_quantity + :quantity
        where sale_product_id = :saleProductId
          and reserved_quantity >= :quantity
        """, nativeQuery = true)
    int confirmReservationIfAvailable(
        @Param("saleProductId") Long saleProductId,
        @Param("quantity") long quantity
    );

    @Modifying
    @Query(value = """
        update inventories
        set available_quantity = available_quantity + :quantity,
            reserved_quantity = reserved_quantity - :quantity
        where sale_product_id = :saleProductId
          and reserved_quantity >= :quantity
        """, nativeQuery = true)
    int releaseReservationIfAvailable(
        @Param("saleProductId") Long saleProductId,
        @Param("quantity") long quantity
    );
}
