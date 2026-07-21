package com.example.limitedticketing.purchase.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PurchaseTest {

    @Test
    void pendingPurchaseCanBeMarkedPaid() {
        Purchase purchase = Purchase.reserve(
            1L,
            1L,
            Money.of(BigDecimal.valueOf(10_000)),
            LocalDateTime.of(2026, 7, 19, 12, 5)
        );

        purchase.markPaid();

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PAID);
    }

    @Test
    void pendingPurchaseExpiresAtExpirationTime() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 19, 12, 5);
        Purchase purchase = Purchase.reserve(1L, 1L, Money.of(BigDecimal.valueOf(10_000)), expiresAt);

        purchase.expire(expiresAt);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        assertThatThrownBy(purchase::markPaid).isInstanceOf(PurchaseException.class);
    }

    @Test
    void onlyPurchaseMemberCanCancelPendingPurchase() {
        Purchase purchase = Purchase.reserve(
            1L,
            1L,
            Money.of(BigDecimal.valueOf(10_000)),
            LocalDateTime.of(2026, 7, 19, 12, 5)
        );

        assertThatThrownBy(() -> purchase.cancelBy(2L)).isInstanceOf(PurchaseException.class);

        purchase.cancelBy(1L);

        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
    }
}
