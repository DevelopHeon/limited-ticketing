package com.example.limitedticketing.purchasewaiting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("구매 대기 요청의 처리 및 복구 상태 전이")
class PurchaseWaitingEntryTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    @DisplayName("ZSET score가 구분할 수 없는 순번은 대기 원장에 생성하지 않는다")
    void rejectsSequenceOutsideExactRedisIntegerRange() {
        assertThatThrownBy(() -> PurchaseWaitingEntry.enqueue(
            UUID.randomUUID(), 1L, 2L, "precision", 9_007_199_254_740_992L, REQUESTED_AT
        )).isInstanceOf(PurchaseWaitingException.class);
    }

    @Test
    @DisplayName("대기 요청은 처리 대상으로 가져온 뒤에만 구매 예약을 완료할 수 있다")
    void waitingRequestMustBeClaimedBeforeItCanComplete() {
        PurchaseWaitingEntry entry = waitingEntry();

        assertThatThrownBy(() -> entry.completeReservation(10L, REQUESTED_AT.plusSeconds(2)))
            .isInstanceOf(PurchaseWaitingException.class).extracting("code").isEqualTo("INVALID_QUEUE_ENTRY_STATE");
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(entry.getPurchaseId()).isNull();

        beginProcessing(entry, UUID.randomUUID(), REQUESTED_AT.plusSeconds(1), REQUESTED_AT.plusSeconds(1).plusSeconds(30));
        entry.completeReservation(10L, REQUESTED_AT.plusSeconds(2));

        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(entry.getPurchaseId()).isEqualTo(10L);
        assertThat(entry.getCompletedAt()).isEqualTo(REQUESTED_AT.plusSeconds(2));
    }

    @Test
    @DisplayName("처리 중인 대기 요청을 다시 처리 대상으로 가져올 수 없다")
    void claimedRequestCannotBeClaimedAgain() {
        PurchaseWaitingEntry entry = waitingEntry();
        LocalDateTime claimedAt = REQUESTED_AT.plusSeconds(1);
        beginProcessing(entry, UUID.randomUUID(), claimedAt, claimedAt.plusSeconds(30));

        assertThatThrownBy(() -> entry.beginProcessing(UUID.randomUUID(), claimedAt.plusSeconds(1), claimedAt.plusSeconds(1).plusSeconds(30)))
            .isInstanceOf(PurchaseWaitingException.class);
        assertThat(entry.getProcessingStartedAt()).isEqualTo(claimedAt);
    }

    @Test
    @DisplayName("복구된 요청은 원래 식별자와 순번 및 멱등성 키를 유지한다")
    void recoveredRequestKeepsItsIdentityAndOriginalSequence() {
        PurchaseWaitingEntry entry = waitingEntry();
        UUID originalId = entry.getId();
        beginProcessing(entry, UUID.randomUUID(), REQUESTED_AT.plusSeconds(1), REQUESTED_AT.plusSeconds(1).plusSeconds(30));

        entry.returnToWaiting();

        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(entry.getId()).isEqualTo(originalId);
        assertThat(entry.getQueueSequence()).isEqualTo(42);
        assertThat(entry.getPurchaseRequestKey()).isEqualTo("purchase-1");
        assertThat(entry.getProcessingStartedAt()).isNull();

        beginProcessing(entry, UUID.randomUUID(), REQUESTED_AT.plusMinutes(1), REQUESTED_AT.plusMinutes(1).plusSeconds(30));
        entry.completeReservation(10L, REQUESTED_AT.plusMinutes(1));
        assertThat(entry.getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @EnumSource(value = PurchaseWaitingStatus.class, names = {"RESERVED", "SOLD_OUT", "FAILED"})
    @DisplayName("완료된 대기 요청은 재처리하거나 결과를 덮어쓸 수 없다")
    void completedRequestCannotBeReprocessedOrHaveItsResultOverwritten(PurchaseWaitingStatus status) {
        PurchaseWaitingEntry entry = waitingEntry();
        beginProcessing(entry, UUID.randomUUID(), REQUESTED_AT.plusSeconds(1), REQUESTED_AT.plusSeconds(1).plusSeconds(30));
        switch (status) {
            case RESERVED -> entry.completeReservation(10L, REQUESTED_AT.plusSeconds(2));
            case SOLD_OUT -> entry.markSoldOut("INSUFFICIENT_INVENTORY", REQUESTED_AT.plusSeconds(2));
            case FAILED -> entry.fail("SALE_UNAVAILABLE", REQUESTED_AT.plusSeconds(2));
            default -> throw new IllegalArgumentException("Expected a completed status");
        }

        assertThatThrownBy(entry::returnToWaiting).isInstanceOf(PurchaseWaitingException.class);
        assertThatThrownBy(() -> entry.beginProcessing(UUID.randomUUID(), REQUESTED_AT.plusSeconds(3), REQUESTED_AT.plusSeconds(3).plusSeconds(30)))
            .isInstanceOf(PurchaseWaitingException.class);
        assertThatThrownBy(() -> entry.completeReservation(20L, REQUESTED_AT.plusSeconds(3)))
            .isInstanceOf(PurchaseWaitingException.class);
        assertThat(entry.getStatus()).isEqualTo(status);
        assertThat(entry.getCompletedAt()).isEqualTo(REQUESTED_AT.plusSeconds(2));
        if (status == PurchaseWaitingStatus.RESERVED) {
            assertThat(entry.getPurchaseId()).isEqualTo(10L);
        } else {
            assertThat(entry.getPurchaseId()).isNull();
            assertThat(entry.getFailureCode())
                .isEqualTo(status == PurchaseWaitingStatus.SOLD_OUT ? "INSUFFICIENT_INVENTORY" : "SALE_UNAVAILABLE");
        }
    }

    @Test
    @DisplayName("복구 후 재처리 시 이전 시도 토큰의 처리 권한은 유지되지 않는다")
    void recoveryInvalidatesThePreviousAttempt() {
        PurchaseWaitingEntry entry = waitingEntry();
        UUID oldToken = UUID.randomUUID();
        UUID newToken = UUID.randomUUID();
        beginProcessing(entry, oldToken, REQUESTED_AT, REQUESTED_AT.plusSeconds(30));
        entry.returnToWaiting();
        beginProcessing(entry, newToken, REQUESTED_AT.plusSeconds(40), REQUESTED_AT.plusSeconds(70));
        assertThat(entry.ownsAttempt(oldToken)).isFalse();
        assertThat(entry.ownsAttempt(newToken)).isTrue();
        assertThat(entry.getQueueSequence()).isEqualTo(42);
    }

    private void beginProcessing(PurchaseWaitingEntry entry, UUID token, LocalDateTime now, LocalDateTime leaseUntil) {
        entry.dispatch(token, now);
        entry.beginProcessing(token, now, leaseUntil);
    }

    private PurchaseWaitingEntry waitingEntry() {
        return PurchaseWaitingEntry.enqueue(UUID.randomUUID(), 1L, 2L, "purchase-1", 42, REQUESTED_AT);
    }
}
