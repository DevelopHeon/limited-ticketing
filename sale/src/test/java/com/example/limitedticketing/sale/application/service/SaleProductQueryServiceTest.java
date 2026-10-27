package com.example.limitedticketing.sale.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.sale.application.port.out.SaleProductSearchPort;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductPage;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductSort;
import com.example.limitedticketing.sale.application.port.out.dto.SortDirection;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductPageResult;
import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("판매 상세 조회의 판매 및 상품 존재 조건")
class SaleProductQueryServiceTest {
    @Mock private SaleProductSearchPort saleProductSearchPort;
    @Mock private ProductRepository productRepository;
    @Mock private SaleProductRepository saleProductRepository;

    @Test
    @DisplayName("없는 판매의 상세 조회는 상품을 조회하지 않고 판매 없음으로 응답한다")
    void missingSaleCannotProduceADetail() {
        when(saleProductRepository.getRequiredById(1L)).thenCallRealMethod();

        assertThatThrownBy(() -> service().loadDetail(new FindSaleProductQuery(1L)))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_PRODUCT_NOT_FOUND");

        verifyNoInteractions(productRepository, saleProductSearchPort);
    }

    @Test
    @DisplayName("판매가 참조하는 상품이 없으면 불완전한 상세 정보를 반환하지 않는다")
    void missingProductCannotProduceAPartialDetail() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 10, 26, 10, 0);
        SaleProduct saleProduct = SaleProduct.createDraft(2L, Money.of(BigDecimal.TEN),
            SalePeriod.of(startsAt, startsAt.plusHours(1)));
        when(saleProductRepository.getRequiredById(1L)).thenCallRealMethod();
        when(saleProductRepository.findById(1L)).thenReturn(Optional.of(saleProduct));
        when(productRepository.getRequiredById(2L)).thenCallRealMethod();

        assertThatThrownBy(() -> service().loadDetail(new FindSaleProductQuery(1L)))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_PRODUCT_NOT_FOUND");
    }

    @Test
    @DisplayName("목록의 여러 판매가 같은 상품을 참조해도 상품을 한 번에 조회하고 판매 순서를 유지한다")
    void pageLoadsDistinctProductsInOneBatchAndKeepsSaleOrdering() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 10, 27, 10, 0);
        SaleProduct first = SaleProduct.reconstitute(10L, 2L, Money.of(BigDecimal.TEN),
            SalePeriod.of(startsAt, startsAt.plusHours(1)), 1, SaleStatus.OPEN);
        SaleProduct second = SaleProduct.reconstitute(11L, 2L, Money.of(BigDecimal.TEN),
            SalePeriod.of(startsAt, startsAt.plusHours(1)), 1, SaleStatus.OPEN);
        when(saleProductSearchPort.findPage(null, 0, 20, SaleProductSort.ID, SortDirection.ASC))
            .thenReturn(new SaleProductPage(List.of(second, first), 2));
        when(productRepository.findAllById(List.of(2L)))
            .thenReturn(List.of(Product.reconstitute(2L, "한정 상품", "상세", true)));

        SaleProductPageResult result = service().loadPage(new FindSaleProductsQuery(null, 0, 20, SaleProductSort.ID, SortDirection.ASC));

        assertThat(result.items()).extracting("saleProductId").containsExactly(11L, 10L);
        assertThat(result.items()).extracting("productName").containsOnly("한정 상품");
        verify(productRepository).findAllById(List.of(2L));
        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("판매 존재 여부 확인은 없는 판매를 예외 대신 거짓으로 반환한다")
    void existenceCheckAllowsAMissingSale() {
        assertThat(service().exists(new FindSaleProductQuery(1L))).isFalse();

        verifyNoInteractions(productRepository, saleProductSearchPort);
    }

    private SaleProductQueryService service() {
        return new SaleProductQueryService(saleProductSearchPort, productRepository, saleProductRepository);
    }
}
