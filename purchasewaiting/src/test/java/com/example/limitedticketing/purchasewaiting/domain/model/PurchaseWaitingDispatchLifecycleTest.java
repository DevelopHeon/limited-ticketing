package com.example.limitedticketing.purchasewaiting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("대기 입장과 실제 실행 시도의 상태 규칙")
class PurchaseWaitingDispatchLifecycleTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);
    private final UUID token = UUID.randomUUID();

    @Test
    @DisplayName("Kafka 전달 대기는 실행 횟수와 실행 lease를 시작하지 않는다")
    void dispatchDoesNotStartExecutionLease() {
        PurchaseWaitingEntry entry = entry();
        entry.dispatch(token, now);
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        assertThat(entry.getExecutionAttempts()).isZero();
        assertThat(entry.getProcessingStartedAt()).isNull();
        assertThat(entry.getLeaseUntil()).isNull();
        assertThat(entry.getDispatchedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("자동 재시도와 재구성 이후에도 실제 실행은 총 세 번까지만 시작한다")
    void executionBudgetSurvivesRetries() {
        PurchaseWaitingEntry entry = entry();
        entry.dispatch(token, now);
        for (int attempt = 1; attempt <= 3; attempt++) {
            entry.beginProcessing(token, now, now.plusSeconds(30));
            assertThat(entry.getExecutionAttempts()).isEqualTo(attempt);
            entry.retryDispatched();
        }
        PurchaseWaitingEntry restored = PurchaseWaitingEntry.reconstitute(entry.getId(), entry.getMemberId(),
            entry.getSaleProductId(), entry.getPurchaseRequestKey(), entry.getQueueSequence(), entry.getStatus(),
            entry.getPurchaseId(), entry.getFailureCode(), entry.getCreatedAt(), entry.getProcessingStartedAt(),
            entry.getCompletedAt(), entry.getProcessingToken(), entry.getLeaseUntil(), entry.getDispatchedAt(), entry.getExecutionAttempts());
        assertThatThrownBy(() -> restored.beginProcessing(token, now, now.plusSeconds(30)))
            .isInstanceOf(PurchaseWaitingException.class);
        assertThat(entry.getExecutionAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("이전 입장 토큰은 새로운 입장의 예약 실행을 시작하지 못한다")
    void staleTokenCannotStart() {
        PurchaseWaitingEntry entry = entry();
        entry.dispatch(token, now);
        assertThatThrownBy(() -> entry.beginProcessing(UUID.randomUUID(), now, now.plusSeconds(30)))
            .isInstanceOf(PurchaseWaitingException.class);
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        assertThat(entry.getExecutionAttempts()).isZero();
    }

    private PurchaseWaitingEntry entry() {
        return PurchaseWaitingEntry.enqueue(UUID.randomUUID(), 1L, 1L, "request-key", 1L, now);
    }
}
