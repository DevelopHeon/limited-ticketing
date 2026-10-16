package com.example.limitedticketing.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.payment.application.port.out.InventoryConfirmationPort;
import com.example.limitedticketing.payment.application.port.out.PurchasePaymentPort;
import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentStatus;
import com.example.limitedticketing.payment.application.port.out.dto.PurchasePaymentTarget;
import com.example.limitedticketing.payment.application.usecase.dto.command.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.command.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.command.FailPaymentCommand;
import com.example.limitedticketing.payment.application.usecase.dto.result.PaymentAttemptResult;
import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.model.PaymentStatus;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 서비스의 소유권 및 예약 유효성 정책")
class PaymentCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final Money PRICE = Money.of(BigDecimal.valueOf(10_000));

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PurchasePaymentPort purchasePaymentPort;
    @Mock
    private InventoryConfirmationPort inventoryConfirmationPort;

    private PaymentCommandService paymentCommandService;

    @BeforeEach
    void createService() {
        paymentCommandService = new PaymentCommandService(paymentRepository, purchasePaymentPort, inventoryConfirmationPort, CLOCK);
    }

    @Test
    @DisplayName("예약 만료 직전의 승인은 구매를 완료하고 예약 재고 한 개를 판매 수량으로 전환한다")
    void approvalBeforeTheDeadlineCompletesPurchaseAndInventory() {
        Payment payment = payment(PaymentStatus.READY);
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(1L, NOW.plusNanos(1)));

        PaymentAttemptResult result = paymentCommandService.approve(new ApprovePaymentCommand(1L, 20L));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(NOW);
        verify(purchasePaymentPort).markPaid(10L);
        verify(inventoryConfirmationPort).confirmReservation(2L, 1);
        verify(paymentRepository).transitionFromReady(payment);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}초]")
    @ValueSource(longs = {0, -1})
    @DisplayName("예약 만료 시각 이후에는 새 결제 시도를 생성하지 않는다")
    void expiredReservationCannotCreatePayment(long secondsUntilExpiration) {
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(1L, NOW.plusSeconds(secondsUntilExpiration)));

        assertThatThrownBy(() -> paymentCommandService.createPayment(new CreatePaymentCommand(1L, 10L, "payment-1")))
            .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PURCHASE_RESERVATION_EXPIRED");

        verify(paymentRepository, never()).saveIfAbsent(any());
        verify(purchasePaymentPort, never()).markPaid(any());
        verifyNoInteractions(inventoryConfirmationPort);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}초]")
    @ValueSource(longs = {0, -1})
    @DisplayName("예약 만료 시각 이후의 승인은 결제 상태와 구매 및 재고를 변경하지 않는다")
    void expiredReservationCannotBeApproved(long secondsUntilExpiration) {
        Payment payment = payment(PaymentStatus.READY);
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(1L, NOW.plusSeconds(secondsUntilExpiration)));

        assertThatThrownBy(() -> paymentCommandService.approve(new ApprovePaymentCommand(1L, 20L)))
            .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PURCHASE_RESERVATION_EXPIRED");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(paymentRepository, never()).transitionFromReady(any());
        verify(purchasePaymentPort, never()).markPaid(any());
        verifyNoInteractions(inventoryConfirmationPort);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"create", "approve", "fail"})
    @DisplayName("다른 회원은 결제를 생성하거나 승인 및 실패 처리할 수 없다")
    void anotherMemberCannotChangePayment(String operation) {
        Payment payment = payment(PaymentStatus.READY);
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(2L, NOW.plusMinutes(5)));
        if (!operation.equals("create")) {
            when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        }

        assertThatThrownBy(() -> {
            switch (operation) {
                case "create" -> paymentCommandService.createPayment(new CreatePaymentCommand(1L, 10L, "payment-1"));
                case "approve" -> paymentCommandService.approve(new ApprovePaymentCommand(1L, 20L));
                case "fail" -> paymentCommandService.fail(new FailPaymentCommand(1L, 20L));
                default -> throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PAYMENT_OWNERSHIP_VIOLATION");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(paymentRepository, never()).saveIfAbsent(any());
        verify(paymentRepository, never()).transitionFromReady(any());
        verify(purchasePaymentPort, never()).markPaid(any());
        verifyNoInteractions(inventoryConfirmationPort);
    }

    @Test
    @DisplayName("실패한 결제의 같은 요청 키 재시도는 기존 실패 결과를 반환하고 결제를 새로 생성하지 않는다")
    void retryOfFailedPaymentReturnsTheExistingAttempt() {
        when(paymentRepository.findByPaymentRequestKey("payment-1")).thenReturn(Optional.of(payment(PaymentStatus.FAILED)));
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(1L, NOW.plusMinutes(5)));

        PaymentAttemptResult result = paymentCommandService.createPayment(new CreatePaymentCommand(1L, 10L, "payment-1"));

        assertThat(result).isEqualTo(new PaymentAttemptResult(20L, PaymentStatus.FAILED, PRICE));
        verify(paymentRepository, never()).saveIfAbsent(any());
        verify(paymentRepository, never()).transitionFromReady(any());
        verify(purchasePaymentPort, never()).markPaid(any());
        verifyNoInteractions(inventoryConfirmationPort);
    }

    @Test
    @DisplayName("다른 구매에 결제 요청 키를 재사용하면 원래 결제와 두 구매를 변경하지 않는다")
    void paymentRequestKeyCannotBeReusedForAnotherPurchase() {
        when(paymentRepository.findByPaymentRequestKey("payment-1")).thenReturn(Optional.of(payment(PaymentStatus.READY)));

        assertThatThrownBy(() -> paymentCommandService.createPayment(new CreatePaymentCommand(1L, 11L, "payment-1")))
            .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PAYMENT_REQUEST_KEY_REUSE");

        verify(paymentRepository, never()).saveIfAbsent(any());
        verify(paymentRepository, never()).transitionFromReady(any());
        verifyNoInteractions(purchasePaymentPort, inventoryConfirmationPort);
    }

    @Test
    @DisplayName("결제 실패는 해당 시도만 실패로 기록하고 구매 예약과 재고를 유지한다")
    void failedAttemptKeepsThePurchaseAndInventoryReservation() {
        Payment payment = payment(PaymentStatus.READY);
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment));
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(1L, NOW.plusMinutes(5)));

        PaymentAttemptResult result = paymentCommandService.fail(new FailPaymentCommand(1L, 20L));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getApprovedAt()).isNull();
        verify(paymentRepository).transitionFromReady(payment);
        verify(purchasePaymentPort, never()).markPaid(any());
        verifyNoInteractions(inventoryConfirmationPort);
    }

    @Test
    @DisplayName("구매 완료의 상태 전이가 경합으로 거부되면 재고 확정과 결제 저장을 진행하지 않는다")
    void rejectedPurchaseTransitionDoesNotConfirmInventoryOrPersistApproval() {
        when(paymentRepository.findById(20L)).thenReturn(Optional.of(payment(PaymentStatus.READY)));
        when(purchasePaymentPort.findPaymentTarget(10L))
            .thenReturn(target(1L, NOW.plusMinutes(5)));
        doThrow(PurchaseException.invalidState("이미 처리된 구매")).when(purchasePaymentPort).markPaid(any());

        assertThatThrownBy(() -> paymentCommandService.approve(new ApprovePaymentCommand(1L, 20L)))
            .isInstanceOf(PurchaseException.class).extracting("code").isEqualTo("INVALID_PURCHASE_STATE");

        verify(paymentRepository, never()).transitionFromReady(any());
        verifyNoInteractions(inventoryConfirmationPort);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.reconstitute(20L, 10L, "payment-1", PRICE, status, null);
    }

    private PurchasePaymentTarget target(Long ownerId, LocalDateTime expiresAt) {
        return new PurchasePaymentTarget(10L, ownerId, 2L, PRICE, 1, expiresAt, PurchasePaymentStatus.PENDING_PAYMENT);
    }
}
