package com.example.limitedticketing.purchase.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("구매의 소유권과 허용 상태 전이")
class PurchaseTest {

    @Test
    @DisplayName("구매 예약은 만료 시각 이전에 만료 처리할 수 없다")
    void purchaseCannotExpireBeforeItsDeadline() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 15, 12, 5);
        Purchase purchase = Purchase.reserve(1L, 1L, "purchase-1", Money.of(BigDecimal.valueOf(10_000)), expiresAt);

        assertThatThrownBy(() -> purchase.expire(expiresAt.minusNanos(1))).isInstanceOf(PurchaseException.class);
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PENDING_PAYMENT);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @EnumSource(value = PurchaseStatus.class, names = "PENDING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("종료된 구매는 결제 완료와 취소 및 만료로 다시 전이할 수 없다")
    void completedPurchaseCannotBePaidCancelledOrExpiredAgain(PurchaseStatus status) {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 15, 12, 5);
        Purchase purchase = Purchase.reconstitute(
            1L, 1L, 1L, "purchase-1", Money.of(BigDecimal.valueOf(10_000)), 1, expiresAt, status
        );

        assertThatThrownBy(purchase::markPaid).isInstanceOf(PurchaseException.class);
        assertThatThrownBy(() -> purchase.cancelBy(1L)).isInstanceOf(PurchaseException.class);
        assertThatThrownBy(() -> purchase.expire(expiresAt)).isInstanceOf(PurchaseException.class);
        assertThat(purchase.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("결제 대기 구매는 결제 완료로 전이할 수 있다")
    void pendingPurchaseCanBeMarkedPaid() {
        Purchase purchase = Purchase.reserve(
            1L,
            1L,
            "purchase-request-1",
            Money.of(BigDecimal.valueOf(10_000)),
            LocalDateTime.of(2026, 7, 19, 12, 5)
        );

        purchase.markPaid();

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PAID);
    }

    @Test
    @DisplayName("예약 만료 시각에는 구매가 만료되고 이후 결제 완료할 수 없다")
    void pendingPurchaseExpiresAtExpirationTime() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 19, 12, 5);
        Purchase purchase = Purchase.reserve(1L, 1L, "purchase-request-1", Money.of(BigDecimal.valueOf(10_000)), expiresAt);

        purchase.expire(expiresAt);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        assertThatThrownBy(purchase::markPaid).isInstanceOf(PurchaseException.class);
    }

    @Test
    @DisplayName("구매 소유자만 결제 대기 구매를 취소할 수 있다")
    void onlyPurchaseMemberCanCancelPendingPurchase() {
        Purchase purchase = Purchase.reserve(
            1L,
            1L,
            "purchase-request-1",
            Money.of(BigDecimal.valueOf(10_000)),
            LocalDateTime.of(2026, 7, 19, 12, 5)
        );

        assertThatThrownBy(() -> purchase.cancelBy(2L)).isInstanceOf(PurchaseException.class);

        purchase.cancelBy(1L);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
    }
}
