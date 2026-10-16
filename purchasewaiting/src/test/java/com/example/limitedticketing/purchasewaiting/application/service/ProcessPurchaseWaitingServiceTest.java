package com.example.limitedticketing.purchasewaiting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import com.example.limitedticketing.shared.exception.DomainErrorType;
import com.example.limitedticketing.shared.exception.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("구매 대기 처리 서비스의 결과 분류 및 복구 정책")
class ProcessPurchaseWaitingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PurchaseReservationPort purchaseReservationPort;
    @Mock
    private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;

    private final UUID token = UUID.randomUUID();
    private PurchaseWaitingEntry entry;
    private ProcessPurchaseWaitingService processPurchaseWaitingService;

    @BeforeEach
    void createService() {
        entry = PurchaseWaitingEntry.enqueue(UUID.randomUUID(), 1L, 2L, "purchase-1", 42, NOW.minusMinutes(1));
        entry.beginProcessing(token, NOW.minusSeconds(1), NOW.plusSeconds(30));
        when(purchaseWaitingEntryRepository.findByIdForUpdate(entry.getId())).thenReturn(Optional.of(entry));
        processPurchaseWaitingService = new ProcessPurchaseWaitingService(purchaseWaitingEntryRepository, new ReserveWaitingPurchaseService(purchaseReservationPort), CLOCK);
    }

    @Test
    @DisplayName("이전 시도는 새 토큰의 요청을 예약하거나 완료할 수 없다")
    void staleAttemptCannotReserveOrComplete() {
        assertThat(processPurchaseWaitingService.process(new ProcessPurchaseWaitingCommand(entry.getId(), UUID.randomUUID()))).isEmpty();
        verify(purchaseReservationPort, never()).reserve(any(), any(), any());
        verify(purchaseWaitingEntryRepository, never()).save(any());
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.PROCESSING);
    }

    @Test
    @DisplayName("이미 만료된 처리 권한으로 새 구매 예약을 시작하지 않는다")
    void expiredAttemptCannotStartAReservation() {
        entry.returnToWaiting();
        entry.beginProcessing(token, NOW.minusSeconds(40), NOW.minusSeconds(10));
        assertThat(processPurchaseWaitingService.process(new ProcessPurchaseWaitingCommand(entry.getId(), token))).isEmpty();
        verify(purchaseReservationPort, never()).reserve(any(), any(), any());
    }

    @Test
    @DisplayName("예약 성공은 대기 요청에 구매 식별자와 완료 시각을 기록한다")
    void successfulReservationCompletesTheWaitingRequestWithItsPurchaseId() {
        when(purchaseReservationPort.reserve(1L, 2L, "purchase-1"))
            .thenReturn(10L);
        when(purchaseWaitingEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseWaitingEntry result = processPurchaseWaitingService.process(new ProcessPurchaseWaitingCommand(entry.getId(), token)).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(result.getPurchaseId()).isEqualTo(10L);
        assertThat(result.getCompletedAt()).isEqualTo(NOW);
        assertThat(result.getFailureCode()).isNull();
        verify(purchaseWaitingEntryRepository).save(entry);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @CsvSource({"INSUFFICIENT_INVENTORY,SOLD_OUT", "SALE_UNAVAILABLE,FAILED", "MEMBER_NOT_PURCHASABLE,FAILED"})
    @DisplayName("재고 부족은 품절로 분류하고 나머지 업무 거부는 실패로 원래 오류 코드와 함께 기록한다")
    void businessRejectionIsRecordedWithItsOriginalFailureCode(String code, PurchaseWaitingStatus expectedStatus) {
        DomainException rejection = new DomainException(DomainErrorType.CONFLICT, code, "구매 예약 거부") { };
        when(purchaseReservationPort.reserve(any(), any(), any())).thenThrow(rejection);
        when(purchaseWaitingEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseWaitingEntry result = processPurchaseWaitingService.process(new ProcessPurchaseWaitingCommand(entry.getId(), token)).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getFailureCode()).isEqualTo(code);
        assertThat(result.getPurchaseId()).isNull();
        assertThat(result.getCompletedAt()).isEqualTo(NOW);
        verify(purchaseWaitingEntryRepository).save(entry);
    }

    @Test
    @DisplayName("예상하지 못한 시스템 오류는 전파하고 대기 요청을 복구 가능한 처리 중 상태로 남긴다")
    void systemFailureIsPropagatedAndLeavesTheRequestRecoverable() {
        IllegalStateException failure = new IllegalStateException("구매 저장소 연결 실패");
        when(purchaseReservationPort.reserve(any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> processPurchaseWaitingService.process(new ProcessPurchaseWaitingCommand(entry.getId(), token))).isSameAs(failure);

        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.PROCESSING);
        assertThat(entry.getCompletedAt()).isNull();
        assertThat(entry.getFailureCode()).isNull();
        assertThat(entry.getPurchaseId()).isNull();
        verify(purchaseWaitingEntryRepository, never()).save(any());
    }
}
