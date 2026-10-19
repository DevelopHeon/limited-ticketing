package com.example.limitedticketing.purchasewaiting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationLookupPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationExecutionResult;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("예약 실행 오류의 지속되는 시도 예산과 결과 복원")
class RecordPurchaseWaitingFailureServiceTest {
    @Mock private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Mock private PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    @Mock private PurchaseReservationLookupPort purchaseReservationLookupPort;
    private RecordPurchaseWaitingFailureService recordPurchaseWaitingFailureService;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);
    private final UUID token = UUID.randomUUID();

    @BeforeEach
    void prepare() {
        recordPurchaseWaitingFailureService = new RecordPurchaseWaitingFailureService(purchaseWaitingEntryRepository,
            purchaseWaitingOutboxPort, purchaseReservationLookupPort, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("세 번째 실패여도 이미 커밋된 구매가 있으면 예약 결과를 복원하고 DLT는 만들지 않는다")
    void restoresCommittedPurchaseInsteadOfReportingFailure() {
        PurchaseWaitingEntry entry = processing(3);
        when(purchaseReservationLookupPort.findPurchaseId(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey())).thenReturn(Optional.of(91L));
        assertThat(recordPurchaseWaitingFailureService.record(command(entry))).isEqualTo(ReservationExecutionResult.COMPLETED);
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(entry.getPurchaseId()).isEqualTo(91L);
        verify(purchaseWaitingOutboxPort, never()).append(any());
    }

    @Test
    @DisplayName("세 번 소진하고 구매도 없을 때만 실패 상태와 DLT 알림을 함께 저장한다")
    void recordsFailureAndDeadLetterAfterBudget() {
        PurchaseWaitingEntry entry = processing(3);
        when(purchaseReservationLookupPort.findPurchaseId(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey())).thenReturn(Optional.empty());
        assertThat(recordPurchaseWaitingFailureService.record(command(entry))).isEqualTo(ReservationExecutionResult.COMPLETED);
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.FAILED);
        assertThat(entry.getFailureCode()).isEqualTo(PurchaseWaitingEntry.TECHNICAL_FAILURE_CODE);
        verify(purchaseWaitingEntryRepository).save(entry);
        verify(purchaseWaitingOutboxPort).append(any());
    }

    @Test
    @DisplayName("첫 번째 오류는 실행 횟수를 보존한 채 전달 대기로 돌아가며 실패 확정하지 않는다")
    void preservesBudgetForNextDelivery() {
        PurchaseWaitingEntry entry = processing(1);
        assertThat(recordPurchaseWaitingFailureService.record(command(entry))).isEqualTo(ReservationExecutionResult.RETRY);
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        assertThat(entry.getExecutionAttempts()).isEqualTo(1);
        assertThat(entry.getLeaseUntil()).isNull();
        verify(purchaseReservationLookupPort, never()).findPurchaseId(any(), any(), any());
        verify(purchaseWaitingOutboxPort, never()).append(any());
    }

    private PurchaseWaitingEntry processing(int count) {
        PurchaseWaitingEntry entry = PurchaseWaitingEntry.enqueue(UUID.randomUUID(), 1L, 2L, "request-key", 1L, now);
        entry.dispatch(token, now);
        for (int attempt = 0; attempt < count; attempt++) {
            entry.beginProcessing(token, now, now.plusSeconds(30));
            if (attempt + 1 < count) {
                entry.retryDispatched();
            }
        }
        when(purchaseWaitingEntryRepository.findByIdForUpdate(entry.getId())).thenReturn(Optional.of(entry));
        return entry;
    }

    private ReservationExecutionCommand command(PurchaseWaitingEntry entry) {
        return new ReservationExecutionCommand(UUID.randomUUID(), entry.getId(), entry.getSaleProductId(), token);
    }
}
