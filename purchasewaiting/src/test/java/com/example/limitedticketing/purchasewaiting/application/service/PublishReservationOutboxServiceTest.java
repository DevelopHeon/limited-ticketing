package com.example.limitedticketing.purchasewaiting.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 발행 확인과 안전한 재전달")
class PublishReservationOutboxServiceTest {
    @Mock private PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    @Mock private ReservationMessagePublisherPort reservationMessagePublisherPort;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Test
    @DisplayName("Kafka ACK를 받지 못하면 발행 완료로 표시하지 않고 지연 후 다시 전달한다")
    void failedAckKeepsMessagePending() {
        UUID id = UUID.randomUUID();
        when(purchaseWaitingOutboxPort.findReadyForUpdate(now, 10)).thenReturn(List.of(new ReservationOutboxMessage(id, "requests", "1", "payload", 0)));
        doThrow(new IllegalStateException("broker unavailable")).when(reservationMessagePublisherPort).publish(anyString(), anyString(), anyString());
        service().publishReady();
        verify(purchaseWaitingOutboxPort, never()).markPublished(any(), any());
        verify(purchaseWaitingOutboxPort).recordFailure(eq(id), eq(now.plusSeconds(1)), anyString());
    }

    @Test
    @DisplayName("이전 발행이 반복 실패했어도 재전달 대기 간격은 30초를 넘지 않는다")
    void retryDelayHasAnUpperBound() {
        UUID id = UUID.randomUUID();
        when(purchaseWaitingOutboxPort.findReadyForUpdate(now, 10)).thenReturn(List.of(new ReservationOutboxMessage(id, "requests", "1", "payload", 50)));
        doThrow(new IllegalStateException("broker unavailable")).when(reservationMessagePublisherPort).publish(anyString(), anyString(), anyString());
        service().publishReady();
        verify(purchaseWaitingOutboxPort).recordFailure(eq(id), eq(now.plusSeconds(30)), anyString());
    }

    private PublishReservationOutboxService service() {
        return new PublishReservationOutboxService(purchaseWaitingOutboxPort, reservationMessagePublisherPort,
            Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("전송이 지연되어 실패해도 실제 실패한 시각부터 재시도 간격을 보장한다")
    void retryDelayStartsAfterTheSendFails() {
        UUID id = UUID.randomUUID();
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(now.toInstant(ZoneOffset.UTC), now.plusSeconds(10).toInstant(ZoneOffset.UTC));
        when(purchaseWaitingOutboxPort.findReadyForUpdate(now, 10)).thenReturn(List.of(new ReservationOutboxMessage(id, "requests", "1", "payload", 0)));
        doThrow(new IllegalStateException("delayed broker failure")).when(reservationMessagePublisherPort).publish(anyString(), anyString(), anyString());
        new PublishReservationOutboxService(purchaseWaitingOutboxPort, reservationMessagePublisherPort, clock).publishReady();
        verify(purchaseWaitingOutboxPort).recordFailure(eq(id), eq(now.plusSeconds(11)), anyString());
    }
}
