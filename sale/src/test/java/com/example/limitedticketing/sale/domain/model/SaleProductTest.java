package com.example.limitedticketing.sale.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("판매 기간과 판매 상태에 따른 구매 가능 정책")
class SaleProductTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 10, 15, 12, 0);
    private static final LocalDateTime ENDS_AT = STARTS_AT.plusHours(1);

    @Test
    @DisplayName("판매 기간 안이라도 초안 상태의 상품은 구매할 수 없다")
    void draftCannotBePurchasedEvenDuringTheSalePeriod() {
        SaleProduct sale = draft();

        assertThatThrownBy(() -> sale.ensurePurchasableAt(STARTS_AT))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_UNAVAILABLE");
    }

    @Test
    @DisplayName("판매 시작 시각부터 개시하고 종료 직전까지 구매할 수 있다")
    void saleCanOpenAndAcceptPurchasesAtTheStartingTime() {
        SaleProduct sale = draft();

        sale.open(STARTS_AT);

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.OPEN);
        assertThatCode(() -> sale.ensurePurchasableAt(STARTS_AT)).doesNotThrowAnyException();
        assertThatCode(() -> sale.ensurePurchasableAt(ENDS_AT.minusNanos(1))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(longs = {-1, 3600})
    @DisplayName("판매 기간 밖에서는 판매를 개시할 수 없다")
    void saleCannotOpenOutsideItsPeriod(long secondsFromStart) {
        SaleProduct sale = draft();

        assertThatThrownBy(() -> sale.open(STARTS_AT.plusSeconds(secondsFromStart)))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_OUTSIDE_PERIOD");
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.DRAFT);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(longs = {-1, 3600})
    @DisplayName("개시된 판매도 판매 기간 밖의 구매는 거부한다")
    void openSaleRejectsPurchasesOutsideItsPeriod(long secondsFromStart) {
        SaleProduct sale = draft();
        sale.open(STARTS_AT);

        assertThatThrownBy(() -> sale.ensurePurchasableAt(STARTS_AT.plusSeconds(secondsFromStart)))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_UNAVAILABLE");
    }

    @Test
    @DisplayName("개시 후 판매 기간을 변경할 수 없고 종료 후 구매하거나 재개시할 수 없다")
    void openingSaleFreezesItsPeriodAndClosingStopsPurchases() {
        SaleProduct sale = draft();
        sale.open(STARTS_AT);

        assertThatThrownBy(() -> sale.changeSalePeriod(SalePeriod.of(STARTS_AT, ENDS_AT.plusHours(1))))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("INVALID_SALE_STATE");
        assertThat(sale.getSalePeriod().getEndsAt()).isEqualTo(ENDS_AT);

        sale.close();

        assertThatThrownBy(() -> sale.ensurePurchasableAt(STARTS_AT.plusMinutes(1)))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_UNAVAILABLE");
        assertThatThrownBy(() -> sale.open(STARTS_AT.plusMinutes(1)))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("INVALID_SALE_STATE");
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.CLOSED);
    }

    private SaleProduct draft() {
        return SaleProduct.createDraft(1L, Money.of(BigDecimal.valueOf(10_000)), SalePeriod.of(STARTS_AT, ENDS_AT));
    }
}
