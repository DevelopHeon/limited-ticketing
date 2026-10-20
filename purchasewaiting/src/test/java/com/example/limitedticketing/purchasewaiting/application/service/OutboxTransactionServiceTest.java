package com.example.limitedticketing.purchasewaiting.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("발행 실패 시각과 재시도 간격")
class OutboxTransactionServiceTest {

    @ParameterizedTest
    @CsvSource({"1,1", "2,2", "50,30"})
    @DisplayName("전송이 늦게 실패해도 실제 실패 시각부터 지수 backoff를 적용하며 30초를 넘지 않는다")
    void delayedFailureUsesCurrentTimeAndBoundedBackoff(int attempts, long delay) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 19, 10, 0);
        AtomicReference<Instant> current = new AtomicReference<>(start.toInstant(ZoneOffset.UTC));
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenAnswer(invocation -> current.get());
        PurchaseWaitingOutboxPort purchaseWaitingOutboxPort = mock(PurchaseWaitingOutboxPort.class);
        ReservationMessagePublisherPort reservationMessagePublisherPort = mock(ReservationMessagePublisherPort.class);
        ReservationOutboxClaim claim = new ReservationOutboxClaim(
            new ReservationOutboxMessage(UUID.randomUUID(), "requests", "1", "payload", attempts), UUID.randomUUID());
        when(purchaseWaitingOutboxPort.claimReady(any(), any(), any(), anyInt())).thenReturn(List.of(claim));
        CompletableFuture<Void> acknowledgment = new CompletableFuture<>();
        when(reservationMessagePublisherPort.publish(any(ReservationOutboxMessage.class))).thenReturn(acknowledgment);
        OutboxTransactionService outboxTransactionService = new OutboxTransactionService(purchaseWaitingOutboxPort, clock, 30);
        PublishReservationOutboxService service = new PublishReservationOutboxService(outboxTransactionService, reservationMessagePublisherPort, Runnable::run, 1, 1);

        service.publishReady();
        current.set(start.plusSeconds(10).toInstant(ZoneOffset.UTC));
        acknowledgment.completeExceptionally(new CompletionException(new IllegalStateException("broker unavailable")));

        verify(purchaseWaitingOutboxPort).recordFailure(eq(claim.message().eventId()), eq(claim.claimId()),
            eq(start.plusSeconds(10)), eq(start.plusSeconds(10 + delay)), eq("IllegalStateException"));
    }
}
