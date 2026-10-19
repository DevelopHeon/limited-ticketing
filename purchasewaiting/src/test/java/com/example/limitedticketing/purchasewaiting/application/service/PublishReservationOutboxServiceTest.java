package com.example.limitedticketing.purchasewaiting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.application.usecase.OutboxTransactionUseCase;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("제한된 비동기 Outbox 발행과 자원 반환")
class PublishReservationOutboxServiceTest {
    private final OutboxTransactionUseCase outboxTransactionUseCase = mock(OutboxTransactionUseCase.class);
    private final ReservationMessagePublisherPort reservationMessagePublisherPort = mock(ReservationMessagePublisherPort.class);
    private final Queue<Runnable> callbacks = new ArrayDeque<>();

    @Test
    @DisplayName("첫 ACK 전에 배치를 전송하되 DB 완료 콜백까지 In-flight 한도를 유지한다")
    void publishesConcurrentlyAndHoldsCapacityUntilDatabaseCompletion() {
        ReservationOutboxClaim first = claim();
        CompletableFuture<Void> firstAck = new CompletableFuture<>();
        when(outboxTransactionUseCase.claimReady(anyInt())).thenReturn(List.of(first, claim()), List.of(claim()));
        when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString()))
            .thenReturn(firstAck, new CompletableFuture<>(), new CompletableFuture<>());
        PublishReservationOutboxService service = service(callbacks::add, 2);
        service.publishReady();
        service.publishReady();
        verify(reservationMessagePublisherPort, times(2)).publish(anyString(), anyString(), anyString());
        firstAck.complete(null);
        service.publishReady();
        verify(reservationMessagePublisherPort, times(2)).publish(anyString(), anyString(), anyString());
        callbacks.remove().run();
        service.publishReady();
        verify(outboxTransactionUseCase).complete(first);
        verify(outboxTransactionUseCase).claimReady(1);
        verify(reservationMessagePublisherPort, times(3)).publish(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("동기 예외와 비동기 전송 실패 모두 실패 기록 후 용량을 반환한다")
    void sendFailureReleasesCapacity(boolean synchronous) {
        ReservationOutboxClaim first = claim();
        IllegalStateException failure = new IllegalStateException("broker unavailable");
        when(outboxTransactionUseCase.claimReady(1)).thenReturn(List.of(first), List.of(claim()));
        if (synchronous) {
            when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString()))
                .thenThrow(failure).thenReturn(new CompletableFuture<>());
        } else {
            when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(failure), new CompletableFuture<>());
        }
        PublishReservationOutboxService service = service(callbacks::add, 1);
        service.publishReady();
        callbacks.remove().run();
        service.publishReady();
        verify(outboxTransactionUseCase).recordFailure(first, failure);
        verify(reservationMessagePublisherPort, times(2)).publish(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("콜백 Executor가 실행을 거부해도 Permit을 반환해 다른 발행을 막지 않는다")
    void rejectedCallbackDoesNotLeakCapacity() {
        when(outboxTransactionUseCase.claimReady(1)).thenReturn(List.of(claim()), List.of(claim()));
        when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        PublishReservationOutboxService service = service(task -> { throw new RejectedExecutionException("queue full"); }, 1);
        service.publishReady();
        service.publishReady();
        verify(reservationMessagePublisherPort, times(2)).publish(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("완료 또는 실패 상태의 DB 저장 오류도 Permit을 영구 점유하지 않는다")
    void databaseRecordingFailureDoesNotLeakCapacity(boolean failedSend) {
        when(outboxTransactionUseCase.claimReady(1)).thenReturn(List.of(claim()), List.of(claim()));
        if (failedSend) {
            when(outboxTransactionUseCase.recordFailure(any(), any())).thenThrow(new IllegalStateException("database unavailable"));
            when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        } else {
            when(outboxTransactionUseCase.complete(any())).thenThrow(new IllegalStateException("database unavailable"));
            when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        }
        PublishReservationOutboxService service = service(callbacks::add, 1);
        service.publishReady();
        callbacks.remove().run();
        service.publishReady();
        verify(reservationMessagePublisherPort, times(2)).publish(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Claim transaction 실패 후에도 다시 같은 용량으로 발행할 수 있다")
    void failedClaimReturnsReservedCapacity() {
        when(outboxTransactionUseCase.claimReady(1)).thenThrow(new IllegalStateException("database unavailable")).thenReturn(List.of(claim()));
        when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        PublishReservationOutboxService service = service(callbacks::add, 1);
        assertThatThrownBy(service::publishReady).isInstanceOf(IllegalStateException.class);
        service.publishReady();
        verify(reservationMessagePublisherPort).publish(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비어 있거나 요청보다 적은 Claim의 남는 Permit은 바로 반환한다")
    void unusedClaimCapacityIsReturnedImmediately() {
        when(outboxTransactionUseCase.claimReady(anyInt())).thenReturn(List.of(), List.of(claim()), List.of(claim()));
        when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        PublishReservationOutboxService service = service(callbacks::add, 2);
        service.publishReady();
        service.publishReady();
        service.publishReady();
        verify(outboxTransactionUseCase).claimReady(1);
        verify(reservationMessagePublisherPort, times(2)).publish(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("동시 polling도 인스턴스 전체 In-flight 한도를 넘지 않는다")
    void concurrentPollsShareOneCapacityLimit() throws Exception {
        AtomicInteger sent = new AtomicInteger();
        when(outboxTransactionUseCase.claimReady(anyInt())).thenAnswer(invocation ->
            IntStream.range(0, invocation.getArgument(0, Integer.class)).mapToObj(index -> claim()).toList());
        when(reservationMessagePublisherPort.publish(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            sent.incrementAndGet();
            return new CompletableFuture<Void>();
        });
        PublishReservationOutboxService service = service(callbacks::add, 4);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = IntStream.range(0, 2).mapToObj(index -> executorService.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("poll start timed out");
                }
                service.publishReady();
                return (Object) null;
            })).toList();
            start.countDown();
            for (Future<?> result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
            assertThat(sent).hasValue(4);
        } finally {
            executorService.shutdownNow();
        }
    }

    private PublishReservationOutboxService service(Executor executor, int capacity) {
        return new PublishReservationOutboxService(outboxTransactionUseCase, reservationMessagePublisherPort, executor, capacity, capacity);
    }

    private ReservationOutboxClaim claim() {
        return new ReservationOutboxClaim(new ReservationOutboxMessage(UUID.randomUUID(), "requests", "1", "payload", 1), UUID.randomUUID());
    }
}
