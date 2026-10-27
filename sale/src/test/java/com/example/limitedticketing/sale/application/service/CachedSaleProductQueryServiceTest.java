package com.example.limitedticketing.sale.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.sale.application.port.out.SaleProductReadCachePort;
import com.example.limitedticketing.sale.application.port.out.dto.CacheFillLease;
import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import com.example.limitedticketing.sale.application.usecase.LoadSaleProductDetailUseCase;
import com.example.limitedticketing.sale.application.usecase.LoadSaleProductsUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductDetailResult;
import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("상품 표시 조회의 캐시 사용과 DB 전환")
class CachedSaleProductQueryServiceTest {
    @Mock private SaleProductReadCachePort saleProductReadCachePort;
    @Mock private LoadSaleProductDetailUseCase loadSaleProductDetailUseCase;
    @Mock private LoadSaleProductsUseCase loadSaleProductsUseCase;
    private CachedSaleProductQueryService cachedSaleProductQueryService;
    private final FindSaleProductQuery query = new FindSaleProductQuery(1L);
    private final CacheFillLease lease = new CacheFillLease(UUID.randomUUID(), UUID.randomUUID(), 1000);

    @BeforeEach
    void createService() {
        cachedSaleProductQueryService = new CachedSaleProductQueryService(saleProductReadCachePort,
            loadSaleProductDetailUseCase, loadSaleProductsUseCase, true, 0, 1);
    }

    @Test
    @DisplayName("캐시 hit는 DB를 읽거나 유효 시간을 연장하지 않고 표시 정보를 반환한다")
    void cacheHitDoesNotReadDatabaseOrExtendTtl() {
        SaleProductDetailResult detail = detail();
        when(saleProductReadCachePort.findDetail(1L)).thenReturn(CacheLookup.hit(detail.toCacheValue()));

        assertThat(cachedSaleProductQueryService.findDetail(query)).isEqualTo(detail);

        verifyNoInteractions(loadSaleProductDetailUseCase, loadSaleProductsUseCase);
        verify(saleProductReadCachePort, never()).storeDetail(1L, lease, detail.toCacheValue());
    }

    @Test
    @DisplayName("재생성 소유자는 DB 결과를 저장하고 소유한 잠금을 반환한다")
    void claimOwnerStoresTheLoadedResultAndReleasesItsLease() {
        SaleProductDetailResult detail = detail();
        when(saleProductReadCachePort.findDetail(1L)).thenReturn(CacheLookup.claimed(lease));
        when(loadSaleProductDetailUseCase.loadDetail(query)).thenReturn(detail);

        assertThat(cachedSaleProductQueryService.findDetail(query)).isEqualTo(detail);

        verify(saleProductReadCachePort).storeDetail(1L, lease, detail.toCacheValue());
        verify(saleProductReadCachePort).releaseDetail(1L, lease);
    }

    @Test
    @DisplayName("없는 상품의 DB 오류는 캐시에 저장하지 않고 잠금을 반환한 뒤 전달한다")
    void missingProductIsNotStoredAsASuccessfulCacheEntry() {
        when(saleProductReadCachePort.findDetail(1L)).thenReturn(CacheLookup.claimed(lease));
        when(loadSaleProductDetailUseCase.loadDetail(query)).thenThrow(SaleException.productNotFound());

        assertThatThrownBy(() -> cachedSaleProductQueryService.findDetail(query))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_PRODUCT_NOT_FOUND");

        verify(saleProductReadCachePort).releaseDetail(1L, lease);
    }

    @Test
    @DisplayName("재생성 대기 상한에 도달하면 다른 소유자의 캐시를 덮어쓰지 않고 DB로 전환한다")
    void busyCacheFallsBackWithoutPublishingAsAnotherOwner() {
        SaleProductDetailResult detail = detail();
        when(saleProductReadCachePort.findDetail(1L)).thenReturn(CacheLookup.busy());
        when(loadSaleProductDetailUseCase.loadDetail(query)).thenReturn(detail);

        assertThat(cachedSaleProductQueryService.findDetail(query)).isEqualTo(detail);

        verify(saleProductReadCachePort, never()).storeDetail(1L, lease, detail.toCacheValue());
        verify(saleProductReadCachePort, never()).releaseDetail(1L, lease);
    }

    @Test
    @DisplayName("캐시 비활성 비교 경로는 Redis를 호출하지 않고 같은 DB loader를 사용한다")
    void disabledCacheUsesTheSameDatabaseLoader() {
        cachedSaleProductQueryService = new CachedSaleProductQueryService(saleProductReadCachePort,
            loadSaleProductDetailUseCase, loadSaleProductsUseCase, false, 0, 1);
        SaleProductDetailResult detail = detail();
        when(loadSaleProductDetailUseCase.loadDetail(query)).thenReturn(detail);

        assertThat(cachedSaleProductQueryService.findDetail(query)).isEqualTo(detail);

        verifyNoInteractions(saleProductReadCachePort);
    }

    private SaleProductDetailResult detail() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 10, 27, 10, 0);
        return new SaleProductDetailResult(1L, "한정 상품", "한정 판매", Money.of(BigDecimal.TEN),
            startsAt, startsAt.plusHours(1), SaleStatus.OPEN);
    }
}
