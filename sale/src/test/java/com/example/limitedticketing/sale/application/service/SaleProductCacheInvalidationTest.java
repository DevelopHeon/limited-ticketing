package com.example.limitedticketing.sale.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.sale.application.port.out.InventoryInitializationPort;
import com.example.limitedticketing.sale.application.port.out.SaleProductReadCachePort;
import com.example.limitedticketing.sale.application.usecase.dto.command.OpenSaleProductCommand;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("판매 변경과 캐시 무효화의 commit 경계")
class SaleProductCacheInvalidationTest {
    @Mock private ProductRepository productRepository;
    @Mock private SaleProductRepository saleProductRepository;
    @Mock private InventoryInitializationPort inventoryInitializationPort;
    @Mock private SaleProductReadCachePort saleProductReadCachePort;

    @BeforeEach
    void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("판매 오픈은 DB 저장 후에도 commit 전에는 캐시를 무효화하지 않는다")
    void invalidationWaitsForCommit() {
        openSale();
        verifyNoInteractions(saleProductReadCachePort);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(saleProductReadCachePort).invalidate();
    }

    @Test
    @DisplayName("판매 변경 rollback은 기존 표시 캐시를 무효화하지 않는다")
    void rollbackKeepsExistingCache() {
        openSale();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(saleProductReadCachePort);
    }

    @Test
    @DisplayName("캐시 무효화 실패를 이미 commit한 판매 오픈의 실패로 바꾸지 않는다")
    void invalidationFailureDoesNotUndoCommittedSale() {
        openSale();
        doThrow(new IllegalStateException("캐시 연결 실패")).when(saleProductReadCachePort).invalidate();

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }

    private void openSale() {
        Clock clock = Clock.systemUTC();
        LocalDateTime now = LocalDateTime.now(clock);
        SaleProduct saleProduct = SaleProduct.createDraft(2L, Money.of(BigDecimal.TEN), SalePeriod.of(now.minusHours(1), now.plusHours(1)));
        when(saleProductRepository.getRequiredById(1L)).thenReturn(saleProduct);
        when(saleProductRepository.save(saleProduct)).thenReturn(saleProduct);
        new SaleProductCommandService(productRepository, saleProductRepository, inventoryInitializationPort, clock, saleProductReadCachePort)
            .open(new OpenSaleProductCommand(1L));
    }
}
