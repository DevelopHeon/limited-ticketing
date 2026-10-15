package com.example.limitedticketing.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.inventory.domain.exception.InventoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("재고 수량 보존과 예약 상태 전이")
class InventoryTest {

    @Test
    @DisplayName("가용 수량을 초과한 예약은 재고를 변경하지 않고 거부한다")
    void oversellingIsRejectedWithoutChangingInventory() {
        Inventory inventory = Inventory.initialize(1L, 1);

        assertThatThrownBy(() -> inventory.reserve(2)).isInstanceOf(InventoryException.class);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getSoldQuantity()).isZero();
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(longs = {0, -1})
    @DisplayName("영 또는 음수 수량의 예약과 확정 및 반환은 재고를 변경하지 않는다")
    void nonPositiveTransitionsCannotIncreaseOrDecreaseInventory(long quantity) {
        Inventory inventory = Inventory.initialize(1L, 2);
        inventory.reserve(1);

        assertThatThrownBy(() -> inventory.reserve(quantity)).isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.confirmReservation(quantity)).isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.releaseReservation(quantity)).isInstanceOf(InventoryException.class);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getSoldQuantity()).isZero();
    }

    @Test
    @DisplayName("예약되지 않은 수량은 판매 확정하거나 반환할 수 없다")
    void unreservedInventoryCannotBeSoldOrReleased() {
        Inventory inventory = Inventory.initialize(1L, 3);
        inventory.reserve(1);

        assertThatThrownBy(() -> inventory.confirmReservation(2)).isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.releaseReservation(2)).isInstanceOf(InventoryException.class);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(2);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getSoldQuantity()).isZero();
    }

    @Test
    @DisplayName("예약 확정은 예약 수량을 판매 수량으로 전환한다")
    void confirmingReservationMovesReservedQuantityToSoldQuantity() {
        Inventory inventory = Inventory.initialize(1L, 1);
        inventory.reserve(1);

        inventory.confirmReservation(1);

        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getSoldQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("예약 해제는 예약 수량을 가용 수량으로 반환한다")
    void releasingReservationReturnsQuantityToAvailableQuantity() {
        Inventory inventory = Inventory.initialize(1L, 1);
        inventory.reserve(1);

        inventory.releaseReservation(1);

        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(inventory.getSoldQuantity()).isZero();
    }
}
