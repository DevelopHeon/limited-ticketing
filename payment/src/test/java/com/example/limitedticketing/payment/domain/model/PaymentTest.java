package com.example.limitedticketing.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void readyPaymentCanFailAndCannotBeApprovedAfterward() {
        Payment payment = Payment.request(1L, "request-1", Money.of(BigDecimal.valueOf(10_000)));

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThatThrownBy(() -> payment.approve(LocalDateTime.of(2026, 7, 19, 12, 0)))
            .isInstanceOf(PaymentException.class);
    }

    @Test
    void readyPaymentCanBeApprovedOnce() {
        Payment payment = Payment.request(1L, "request-1", Money.of(BigDecimal.valueOf(10_000)));
        LocalDateTime approvedAt = LocalDateTime.of(2026, 7, 19, 12, 0);

        payment.approve(approvedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
        assertThatThrownBy(() -> payment.approve(approvedAt))
            .isInstanceOf(PaymentException.class);
    }
}
