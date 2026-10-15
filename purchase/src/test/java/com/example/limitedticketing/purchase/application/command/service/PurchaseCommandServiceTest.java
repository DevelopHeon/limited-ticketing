package com.example.limitedticketing.purchase.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandFacade;
import com.example.limitedticketing.inventory.application.port.in.dto.ReserveInventoryInput;
import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityFacade;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.member.domain.exception.MemberException;
import com.example.limitedticketing.purchase.application.command.dto.CancelPurchaseCommand;
import com.example.limitedticketing.purchase.application.command.dto.PurchaseReservationResult;
import com.example.limitedticketing.purchase.application.command.dto.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.port.out.PurchaseEventPublisher;
import com.example.limitedticketing.purchase.application.support.PurchaseFinder;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.domain.repository.IdempotentPurchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.sale.application.port.in.SaleProductPurchaseFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.PurchasableSaleProduct;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("구매 서비스의 멱등성 및 재고 예약 정책")
class PurchaseCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final Money PRICE = Money.of(BigDecimal.valueOf(10_000));
    private static final ReservePurchaseCommand COMMAND = new ReservePurchaseCommand(1L, 2L, "purchase-1");

    @Mock
    private MemberPurchaseEligibilityFacade memberFacade;
    @Mock
    private SaleProductPurchaseFacade saleFacade;
    @Mock
    private InventoryCommandFacade inventoryFacade;
    @Mock
    private PurchaseRepository repository;
    @Mock
    private PurchaseEventPublisher eventPublisher;

    private PurchaseCommandService service;

    @BeforeEach
    void createService() {
        service = new PurchaseCommandService(
            memberFacade, saleFacade, inventoryFacade, repository, new PurchaseFinder(repository), CLOCK, eventPublisher
        );
    }

    @Test
    @DisplayName("신규 구매는 판매 가격으로 5분간 예약하고 재고 한 개와 예약 이벤트를 생성한다")
    void newPurchaseReservesOneItemForFiveMinutesAndPublishesItsEvent() {
        when(saleFacade.findPurchasable(new FindPurchasableSaleProductInput(2L, NOW)))
            .thenReturn(new PurchasableSaleProduct(2L, PRICE));
        Purchase saved = purchase(2L, PurchaseStatus.PENDING_PAYMENT);
        when(repository.saveIfAbsent(any())).thenReturn(new IdempotentPurchase(saved, true));

        PurchaseReservationResult result = service.reserve(COMMAND);

        ArgumentCaptor<Purchase> submitted = ArgumentCaptor.forClass(Purchase.class);
        verify(repository).saveIfAbsent(submitted.capture());
        assertThat(submitted.getValue().getMemberId()).isEqualTo(1L);
        assertThat(submitted.getValue().getSaleProductId()).isEqualTo(2L);
        assertThat(submitted.getValue().getPrice()).isEqualTo(PRICE);
        assertThat(submitted.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(result).isEqualTo(new PurchaseReservationResult(10L, PurchaseStatus.PENDING_PAYMENT, NOW.plusMinutes(5)));
        verify(memberFacade).ensurePurchasable(new EnsureMemberPurchasableInput(1L));
        verify(inventoryFacade).reserve(new ReserveInventoryInput(2L, 1));
        verify(eventPublisher).publish(saved.reservationEvent());
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @EnumSource(PurchaseStatus.class)
    @DisplayName("같은 요청의 재시도는 구매 상태와 관계없이 기존 결과를 반환하고 재고와 이벤트를 반복하지 않는다")
    void retryReturnsTheExistingPurchaseWithoutRepeatingSideEffects(PurchaseStatus status) {
        Purchase existing = purchase(2L, status);
        when(repository.findByMemberIdAndPurchaseRequestKey(1L, "purchase-1")).thenReturn(Optional.of(existing));

        PurchaseReservationResult result = service.reserve(COMMAND);

        assertThat(result).isEqualTo(new PurchaseReservationResult(10L, status, existing.getExpiresAt()));
        verify(repository, never()).saveIfAbsent(any());
        verifyNoInteractions(memberFacade, saleFacade, inventoryFacade, eventPublisher);
    }

    @Test
    @DisplayName("동시 생성에서 다른 요청이 먼저 구매를 만들면 그 결과를 재사용하고 재고를 추가 예약하지 않는다")
    void concurrentCreationWinnerIsReusedWithoutReservingAgain() {
        when(saleFacade.findPurchasable(any())).thenReturn(new PurchasableSaleProduct(2L, PRICE));
        Purchase winner = purchase(2L, PurchaseStatus.PENDING_PAYMENT);
        when(repository.saveIfAbsent(any())).thenReturn(new IdempotentPurchase(winner, false));

        PurchaseReservationResult result = service.reserve(COMMAND);

        assertThat(result.purchaseId()).isEqualTo(winner.getId());
        assertThat(result.status()).isEqualTo(PurchaseStatus.PENDING_PAYMENT);
        verifyNoInteractions(inventoryFacade, eventPublisher);
    }

    @Test
    @DisplayName("기존 요청 키를 다른 상품에 재사용하면 구매와 재고를 변경하지 않고 거부한다")
    void requestKeyCannotBeReusedForAnotherProduct() {
        when(repository.findByMemberIdAndPurchaseRequestKey(1L, "purchase-1"))
            .thenReturn(Optional.of(purchase(3L, PurchaseStatus.PENDING_PAYMENT)));

        assertThatThrownBy(() -> service.reserve(COMMAND))
            .isInstanceOf(PurchaseException.class).extracting("code").isEqualTo("PURCHASE_REQUEST_KEY_REUSE");

        verify(repository, never()).saveIfAbsent(any());
        verifyNoInteractions(memberFacade, saleFacade, inventoryFacade, eventPublisher);
    }

    @Test
    @DisplayName("회원의 구매 자격이 거부되면 판매 조회와 구매 생성 및 재고 예약을 진행하지 않는다")
    void ineligibleMemberCannotStartAReservation() {
        MemberException rejection = MemberException.notPurchasable();
        doThrow(rejection).when(memberFacade).ensurePurchasable(new EnsureMemberPurchasableInput(1L));

        assertThatThrownBy(() -> service.reserve(COMMAND)).isSameAs(rejection);

        verify(repository, never()).saveIfAbsent(any());
        verifyNoInteractions(saleFacade, inventoryFacade, eventPublisher);
    }

    @Test
    @DisplayName("구매 취소의 상태 전이가 경합으로 거부되면 예약 재고를 반환하지 않는다")
    void rejectedCancellationTransitionDoesNotReleaseInventory() {
        Purchase existing = purchase(2L, PurchaseStatus.PENDING_PAYMENT);
        when(repository.findById(10L)).thenReturn(Optional.of(existing));
        doThrow(PurchaseException.invalidState("이미 처리된 구매"))
            .when(repository).transitionFromPending(existing);

        assertThatThrownBy(() -> service.cancel(new CancelPurchaseCommand(10L, 1L)))
            .isInstanceOf(PurchaseException.class).extracting("code").isEqualTo("INVALID_PURCHASE_STATE");

        verifyNoInteractions(inventoryFacade, eventPublisher);
    }

    private Purchase purchase(Long saleProductId, PurchaseStatus status) {
        return Purchase.reconstitute(10L, 1L, saleProductId, "purchase-1", PRICE, 1, NOW.plusMinutes(5), status);
    }
}
