package com.example.limitedticketing.purchasewaiting.application.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchasewaiting.application.support.ReservationQueueEntryFinder;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
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
class ReservationQueueProcessingServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private PurchaseCommandFacade purchaseFacade;
    @Mock
    private ReservationQueueEntryRepository repository;

    private ReservationQueueEntry entry;
    private ReservationQueueProcessingService service;

    @BeforeEach
    void createService() {
        entry = ReservationQueueEntry.enqueue(UUID.randomUUID(), 1L, 2L, "purchase-1", 42, NOW.minusMinutes(1));
        entry.beginProcessing(NOW.minusSeconds(1));
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        service = new ReservationQueueProcessingService(purchaseFacade, new ReservationQueueCompletionService(
            new ReservationQueueEntryFinder(repository), repository, CLOCK
        ));
    }

    @Test
    @DisplayName("예약 성공은 대기 요청에 구매 식별자와 완료 시각을 기록한다")
    void successfulReservationCompletesTheWaitingRequestWithItsPurchaseId() {
        when(purchaseFacade.reserve(new ReservePurchaseInput(1L, 2L, "purchase-1")))
            .thenReturn(new PurchaseReservation(10L, "PENDING_PAYMENT", NOW.plusMinutes(5)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationQueueEntry result = service.process(entry.getId());

        assertThat(result.getStatus()).isEqualTo(ReservationQueueStatus.RESERVED);
        assertThat(result.getPurchaseId()).isEqualTo(10L);
        assertThat(result.getCompletedAt()).isEqualTo(NOW);
        assertThat(result.getFailureCode()).isNull();
        verify(repository).save(entry);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @CsvSource({"INSUFFICIENT_INVENTORY,SOLD_OUT", "SALE_UNAVAILABLE,FAILED", "MEMBER_NOT_PURCHASABLE,FAILED"})
    @DisplayName("재고 부족은 품절로 분류하고 나머지 업무 거부는 실패로 원래 오류 코드와 함께 기록한다")
    void businessRejectionIsRecordedWithItsOriginalFailureCode(String code, ReservationQueueStatus expectedStatus) {
        DomainException rejection = new DomainException(DomainErrorType.CONFLICT, code, "구매 예약 거부") { };
        when(purchaseFacade.reserve(any())).thenThrow(rejection);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationQueueEntry result = service.process(entry.getId());

        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getFailureCode()).isEqualTo(code);
        assertThat(result.getPurchaseId()).isNull();
        assertThat(result.getCompletedAt()).isEqualTo(NOW);
        verify(repository).save(entry);
    }

    @Test
    @DisplayName("예상하지 못한 시스템 오류는 전파하고 대기 요청을 복구 가능한 처리 중 상태로 남긴다")
    void systemFailureIsPropagatedAndLeavesTheRequestRecoverable() {
        IllegalStateException failure = new IllegalStateException("구매 저장소 연결 실패");
        when(purchaseFacade.reserve(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.process(entry.getId())).isSameAs(failure);

        assertThat(entry.getStatus()).isEqualTo(ReservationQueueStatus.PROCESSING);
        assertThat(entry.getCompletedAt()).isNull();
        assertThat(entry.getFailureCode()).isNull();
        assertThat(entry.getPurchaseId()).isNull();
        verify(repository, never()).save(any());
    }
}
