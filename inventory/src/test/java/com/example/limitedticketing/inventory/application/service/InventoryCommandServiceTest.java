package com.example.limitedticketing.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.inventory.application.usecase.dto.command.InitializeInventoryCommand;
import com.example.limitedticketing.inventory.application.usecase.dto.command.ReserveInventoryCommand;
import com.example.limitedticketing.inventory.domain.exception.InventoryException;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("재고 서비스의 예약 거부와 초기화 보호 정책")
class InventoryCommandServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryCommandService inventoryCommandService;

    @BeforeEach
    void createService() {
        inventoryCommandService = new InventoryCommandService(inventoryRepository);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(longs = {0, -1})
    @DisplayName("0 이하 수량의 예약은 영속 재고를 변경하기 전에 거부한다")
    void nonPositiveReservationDoesNotReachPersistence(long quantity) {
        assertThatThrownBy(() -> inventoryCommandService.reserve(new ReserveInventoryCommand(1L, quantity)))
            .isInstanceOf(InventoryException.class).extracting("code").isEqualTo("INVALID_INVENTORY");

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("재고 원장이 없는 상품의 예약 실패를 품절로 잘못 응답하지 않는다")
    void absentInventoryIsNotReportedAsSoldOut() {
        when(inventoryRepository.findBySaleProductId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryCommandService.reserve(new ReserveInventoryCommand(1L, 1)))
            .isInstanceOf(InventoryException.class).extracting("code").isEqualTo("INVENTORY_NOT_FOUND");
    }

    @Test
    @DisplayName("원장이 존재하지만 예약 가능한 수량이 없으면 품절로 응답한다")
    void exhaustedExistingInventoryIsReportedAsSoldOut() {
        when(inventoryRepository.findBySaleProductId(1L)).thenReturn(Optional.of(Inventory.reconstitute(10L, 1L, 0, 1, 0)));

        assertThatThrownBy(() -> inventoryCommandService.reserve(new ReserveInventoryCommand(1L, 1)))
            .isInstanceOf(InventoryException.class).extracting("code").isEqualTo("INSUFFICIENT_INVENTORY");
    }

    @Test
    @DisplayName("이미 초기화된 상품의 재고를 다시 생성하거나 초기 수량으로 덮어쓰지 않는다")
    void repeatedInitializationDoesNotOverwriteInventory() {
        when(inventoryRepository.findBySaleProductId(1L)).thenReturn(Optional.of(Inventory.initialize(1L, 10)));

        assertThatThrownBy(() -> inventoryCommandService.initialize(new InitializeInventoryCommand(1L, 20)))
            .isInstanceOf(InventoryException.class).extracting("code").isEqualTo("INVENTORY_ALREADY_INITIALIZED");

        verify(inventoryRepository, never()).save(any());
    }
}
