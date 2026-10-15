package com.example.limitedticketing.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제 시도의 허용 상태 전이")
class PaymentTest {

    @Test
    @DisplayName("승인된 결제는 실패로 변경할 수 없고 승인 시각을 유지한다")
    void approvedPaymentCannotFailAndKeepsItsApprovalTime() {
        Payment payment = Payment.request(1L, "payment-1", Money.of(BigDecimal.valueOf(10_000)));
        LocalDateTime approvedAt = LocalDateTime.of(2026, 10, 15, 12, 0);
        payment.approve(approvedAt);

        assertThatThrownBy(payment::fail).isInstanceOf(PaymentException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("준비된 결제는 실패할 수 있지만 실패 후 승인할 수 없다")
    void readyPaymentCanFailAndCannotBeApprovedAfterward() {
        Payment payment = Payment.request(1L, "request-1", Money.of(BigDecimal.valueOf(10_000)));

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThatThrownBy(() -> payment.approve(LocalDateTime.of(2026, 7, 19, 12, 0)))
            .isInstanceOf(PaymentException.class);
    }

    @Test
    @DisplayName("준비된 결제는 한 번만 승인할 수 있다")
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
