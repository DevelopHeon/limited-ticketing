package com.example.limitedticketing.payment.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandFacade;
import com.example.limitedticketing.inventory.application.port.in.dto.ConfirmInventoryReservationInput;
import com.example.limitedticketing.payment.application.command.dto.ApprovePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.CreatePaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.FailPaymentCommand;
import com.example.limitedticketing.payment.application.command.dto.PaymentAttemptResult;
import com.example.limitedticketing.payment.application.support.PaymentFinder;
import com.example.limitedticketing.payment.domain.exception.PaymentException;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.model.PaymentStatus;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
import com.example.limitedticketing.purchase.application.port.in.PurchasePaymentFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.FindPurchaseForPaymentInput;
import com.example.limitedticketing.purchase.application.port.in.dto.MarkPurchasePaidInput;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentStatus;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchasePaymentTarget;
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
    private PaymentRepository repository;
    @Mock
    private PurchasePaymentFacade purchaseFacade;
    @Mock
    private InventoryCommandFacade inventoryFacade;

    private PaymentCommandService service;

    @BeforeEach
    void createService() {
        service = new PaymentCommandService(repository, new PaymentFinder(repository), purchaseFacade, inventoryFacade, CLOCK);
    }

    @Test
    @DisplayName("예약 만료 직전의 승인은 구매를 완료하고 예약 재고 한 개를 판매 수량으로 전환한다")
    void approvalBeforeTheDeadlineCompletesPurchaseAndInventory() {
        Payment payment = payment(PaymentStatus.READY);
        when(repository.findById(20L)).thenReturn(Optional.of(payment));
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(1L, NOW.plusNanos(1)));

        PaymentAttemptResult result = service.approve(new ApprovePaymentCommand(1L, 20L));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(NOW);
        verify(purchaseFacade).markPaid(new MarkPurchasePaidInput(10L));
        verify(inventoryFacade).confirmReservation(new ConfirmInventoryReservationInput(2L, 1));
        verify(repository).transitionFromReady(payment);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}초]")
    @ValueSource(longs = {0, -1})
    @DisplayName("예약 만료 시각 이후에는 새 결제 시도를 생성하지 않는다")
    void expiredReservationCannotCreatePayment(long secondsUntilExpiration) {
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(1L, NOW.plusSeconds(secondsUntilExpiration)));

        assertThatThrownBy(() -> service.createPayment(new CreatePaymentCommand(1L, 10L, "payment-1")))
            .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PURCHASE_RESERVATION_EXPIRED");

        verify(repository, never()).saveIfAbsent(any());
        verify(purchaseFacade, never()).markPaid(any());
        verifyNoInteractions(inventoryFacade);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}초]")
    @ValueSource(longs = {0, -1})
    @DisplayName("예약 만료 시각 이후의 승인은 결제 상태와 구매 및 재고를 변경하지 않는다")
    void expiredReservationCannotBeApproved(long secondsUntilExpiration) {
        Payment payment = payment(PaymentStatus.READY);
        when(repository.findById(20L)).thenReturn(Optional.of(payment));
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(1L, NOW.plusSeconds(secondsUntilExpiration)));

        assertThatThrownBy(() -> service.approve(new ApprovePaymentCommand(1L, 20L)))
            .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PURCHASE_RESERVATION_EXPIRED");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(repository, never()).transitionFromReady(any());
        verify(purchaseFacade, never()).markPaid(any());
        verifyNoInteractions(inventoryFacade);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"create", "approve", "fail"})
    @DisplayName("다른 회원은 결제를 생성하거나 승인 및 실패 처리할 수 없다")
    void anotherMemberCannotChangePayment(String operation) {
        Payment payment = payment(PaymentStatus.READY);
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(2L, NOW.plusMinutes(5)));
        if (!operation.equals("create")) {
            when(repository.findById(20L)).thenReturn(Optional.of(payment));
        }

        assertThatThrownBy(() -> {
            switch (operation) {
                case "create" -> service.createPayment(new CreatePaymentCommand(1L, 10L, "payment-1"));
                case "approve" -> service.approve(new ApprovePaymentCommand(1L, 20L));
                case "fail" -> service.fail(new FailPaymentCommand(1L, 20L));
                default -> throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PAYMENT_OWNERSHIP_VIOLATION");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(repository, never()).saveIfAbsent(any());
        verify(repository, never()).transitionFromReady(any());
        verify(purchaseFacade, never()).markPaid(any());
        verifyNoInteractions(inventoryFacade);
    }

    @Test
    @DisplayName("실패한 결제의 같은 요청 키 재시도는 기존 실패 결과를 반환하고 결제를 새로 생성하지 않는다")
    void retryOfFailedPaymentReturnsTheExistingAttempt() {
        when(repository.findByPaymentRequestKey("payment-1")).thenReturn(Optional.of(payment(PaymentStatus.FAILED)));
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(1L, NOW.plusMinutes(5)));

        PaymentAttemptResult result = service.createPayment(new CreatePaymentCommand(1L, 10L, "payment-1"));

        assertThat(result).isEqualTo(new PaymentAttemptResult(20L, PaymentStatus.FAILED, PRICE));
        verify(repository, never()).saveIfAbsent(any());
        verify(repository, never()).transitionFromReady(any());
        verify(purchaseFacade, never()).markPaid(any());
        verifyNoInteractions(inventoryFacade);
    }

    @Test
    @DisplayName("다른 구매에 결제 요청 키를 재사용하면 원래 결제와 두 구매를 변경하지 않는다")
    void paymentRequestKeyCannotBeReusedForAnotherPurchase() {
        when(repository.findByPaymentRequestKey("payment-1")).thenReturn(Optional.of(payment(PaymentStatus.READY)));

        assertThatThrownBy(() -> service.createPayment(new CreatePaymentCommand(1L, 11L, "payment-1")))
            .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("PAYMENT_REQUEST_KEY_REUSE");

        verify(repository, never()).saveIfAbsent(any());
        verify(repository, never()).transitionFromReady(any());
        verifyNoInteractions(purchaseFacade, inventoryFacade);
    }

    @Test
    @DisplayName("결제 실패는 해당 시도만 실패로 기록하고 구매 예약과 재고를 유지한다")
    void failedAttemptKeepsThePurchaseAndInventoryReservation() {
        Payment payment = payment(PaymentStatus.READY);
        when(repository.findById(20L)).thenReturn(Optional.of(payment));
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(1L, NOW.plusMinutes(5)));

        PaymentAttemptResult result = service.fail(new FailPaymentCommand(1L, 20L));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getApprovedAt()).isNull();
        verify(repository).transitionFromReady(payment);
        verify(purchaseFacade, never()).markPaid(any());
        verifyNoInteractions(inventoryFacade);
    }

    @Test
    @DisplayName("구매 완료의 상태 전이가 경합으로 거부되면 재고 확정과 결제 저장을 진행하지 않는다")
    void rejectedPurchaseTransitionDoesNotConfirmInventoryOrPersistApproval() {
        when(repository.findById(20L)).thenReturn(Optional.of(payment(PaymentStatus.READY)));
        when(purchaseFacade.findPaymentTarget(new FindPurchaseForPaymentInput(10L)))
            .thenReturn(target(1L, NOW.plusMinutes(5)));
        doThrow(PurchaseException.invalidState("이미 처리된 구매")).when(purchaseFacade).markPaid(any());

        assertThatThrownBy(() -> service.approve(new ApprovePaymentCommand(1L, 20L)))
            .isInstanceOf(PurchaseException.class).extracting("code").isEqualTo("INVALID_PURCHASE_STATE");

        verify(repository, never()).transitionFromReady(any());
        verifyNoInteractions(inventoryFacade);
    }

    private Payment payment(PaymentStatus status) {
        return Payment.reconstitute(20L, 10L, "payment-1", PRICE, status, null);
    }

    private PurchasePaymentTarget target(Long ownerId, LocalDateTime expiresAt) {
        return new PurchasePaymentTarget(10L, ownerId, 2L, PRICE, 1, expiresAt, PurchasePaymentStatus.PENDING_PAYMENT);
    }
}
