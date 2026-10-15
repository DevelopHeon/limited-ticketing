package com.example.limitedticketing.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.shared.exception.SharedValueException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("판매 금액의 유효성과 동등성")
class MoneyTest {

    @Test
    @DisplayName("같은 금액은 소수점 자리 수와 관계없이 같은 값으로 취급한다")
    void sameAmountIsEqualRegardlessOfDecimalScale() {
        Money wholeAmount = Money.of(new BigDecimal("10000"));
        Money decimalAmount = Money.of(new BigDecimal("10000.00"));

        assertThat(wholeAmount).isEqualTo(decimalAmount);
        assertThat(new HashSet<>(Set.of(wholeAmount))).contains(decimalAmount);
    }

    @Test
    @DisplayName("판매 가격은 영을 허용하지만 음수는 허용하지 않는다")
    void priceCanBeZeroButCannotBeNegative() {
        assertThat(Money.of(BigDecimal.ZERO).getAmount()).isZero();
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01")))
            .isInstanceOf(SharedValueException.class);
    }
}
