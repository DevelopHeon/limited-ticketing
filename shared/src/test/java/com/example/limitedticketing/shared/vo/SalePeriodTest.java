package com.example.limitedticketing.shared.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.shared.exception.SharedValueException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("판매 기간의 유효성과 시간 경계")
class SalePeriodTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    @DisplayName("판매 기간은 시작 시각을 포함하고 종료 시각을 제외한다")
    void periodIncludesItsStartAndExcludesItsEnd() {
        LocalDateTime endsAt = STARTS_AT.plusHours(1);
        SalePeriod period = SalePeriod.of(STARTS_AT, endsAt);

        assertThat(period.includes(STARTS_AT.minusNanos(1))).isFalse();
        assertThat(period.includes(STARTS_AT)).isTrue();
        assertThat(period.includes(endsAt.minusNanos(1))).isTrue();
        assertThat(period.includes(endsAt)).isFalse();
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(longs = {0, -1})
    @DisplayName("판매 종료 시각은 시작 시각보다 늦어야 한다")
    void saleMustEndAfterItStarts(long endOffsetSeconds) {
        assertThatThrownBy(() -> SalePeriod.of(STARTS_AT, STARTS_AT.plusSeconds(endOffsetSeconds)))
            .isInstanceOf(SharedValueException.class);
    }
}
