package com.example.limitedticketing.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void confirmingReservationMovesReservedQuantityToSoldQuantity() {
        Inventory inventory = Inventory.initialize(1L, 1);
        inventory.reserve(1);

        inventory.confirmReservation(1);

        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getSoldQuantity()).isEqualTo(1);
    }

    @Test
    void releasingReservationReturnsQuantityToAvailableQuantity() {
        Inventory inventory = Inventory.initialize(1L, 1);
        inventory.reserve(1);

        inventory.releaseReservation(1);

        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getSoldQuantity()).isZero();
    }
}
