package com.example.limitedticketing.purchasewaiting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationLookupPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 재처리의 전달 대기와 중복 구매 보호")
class ReplayPurchaseWaitingServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    @Mock private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Mock private PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    @Mock private PurchaseReservationLookupPort purchaseReservationLookupPort;
    @Mock private PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    @BeforeEach
    void beginSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void endSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("입장 후 오래 지났어도 Kafka 발행 후 60초가 지나지 않으면 재처리를 거부한다")
    void recentlyPublishedDeliveryCannotBeReplayed() {
        PurchaseWaitingEntry entry = dispatched();
        givenLocked(entry);
        when(purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), entry.getProcessingToken())).thenReturn(Optional.of(NOW.minusSeconds(59)));
        assertThatThrownBy(() -> service().replay(entry.getId())).isInstanceOf(PurchaseWaitingException.class);
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        verify(purchaseWaitingEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("재처리 대상에 이미 구매가 있으면 새 예약 없이 기존 구매 결과를 복원한다")
    void committedPurchaseIsRestoredWithoutRequeue() {
        PurchaseWaitingEntry entry = dispatched();
        givenLocked(entry);
        when(purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), entry.getProcessingToken())).thenReturn(Optional.of(NOW.minusMinutes(2)));
        when(purchaseReservationLookupPort.findPurchaseId(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey())).thenReturn(Optional.of(91L));
        when(purchaseWaitingEntryRepository.save(entry)).thenReturn(entry);
        PurchaseWaitingResult result = service().replay(entry.getId());
        assertThat(result.status()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(result.purchaseId()).isEqualTo(91L);
    }

    @Test
    @DisplayName("발행 후 정확히 60초 지난 전달 대기를 재처리해도 이미 사용한 실행 횟수는 유지한다")
    void delayedReplayPreservesItsConsumedAttemptBudget() {
        PurchaseWaitingEntry entry = dispatched();
        entry.beginProcessing(entry.getProcessingToken(), NOW.minusSeconds(80), NOW.minusSeconds(50));
        entry.retryDispatched();
        givenLocked(entry);
        when(purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), entry.getProcessingToken())).thenReturn(Optional.of(NOW.minusSeconds(60)));
        when(purchaseReservationLookupPort.findPurchaseId(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey())).thenReturn(Optional.empty());
        when(purchaseWaitingEntryRepository.save(entry)).thenReturn(entry);
        PurchaseWaitingResult replayed = service().replay(entry.getId());
        assertThat(replayed.status()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(entry.getProcessingToken()).isNull();
        assertThat(entry.getExecutionAttempts()).isEqualTo(1);
    }

    private PurchaseWaitingEntry dispatched() {
        PurchaseWaitingEntry entry = PurchaseWaitingEntry.enqueue(UUID.randomUUID(), 1L, 2L, "replay-key", 42, NOW.minusMinutes(3));
        entry.dispatch(UUID.randomUUID(), NOW.minusMinutes(2));
        return entry;
    }

    private void givenLocked(PurchaseWaitingEntry entry) {
        when(purchaseWaitingEntryRepository.findSaleProductId(entry.getId())).thenReturn(Optional.of(entry.getSaleProductId()));
        when(purchaseWaitingEntryRepository.findByIdForUpdate(entry.getId())).thenReturn(Optional.of(entry));
    }

    private ReplayPurchaseWaitingService service() {
        return new ReplayPurchaseWaitingService(purchaseWaitingEntryRepository, purchaseWaitingOutboxPort,
            purchaseReservationLookupPort, purchaseWaitingQueuePort, CLOCK, 10000);
    }
}
