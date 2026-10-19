package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingReservationPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

@DisplayName("예약 메시지의 버전 계약과 안전한 offset 처리")
class ReservationKafkaConsumerTest {
    private final PurchaseWaitingReservationPort purchaseWaitingReservationPort = mock(PurchaseWaitingReservationPort.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final ReservationKafkaConsumer reservationKafkaConsumer = new ReservationKafkaConsumer(
        purchaseWaitingReservationPort, new ObjectMapper(), new SimpleMeterRegistry());

    @Test
    @DisplayName("지원하지 않는 버전은 예약을 실행하거나 offset을 저장하지 않는다")
    void rejectsUnknownSchemaVersion() {
        assertThatThrownBy(() -> reservationKafkaConsumer.receive(record("1", json(2)), acknowledgment)).isInstanceOf(IllegalArgumentException.class);
        verify(purchaseWaitingReservationPort, never()).execute(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("메시지 상품과 partition key가 다르면 업무 입력으로 사용하지 않는다")
    void rejectsMismatchedKey() {
        assertThatThrownBy(() -> reservationKafkaConsumer.receive(record("2", json(1)), acknowledgment)).isInstanceOf(IllegalArgumentException.class);
        verify(purchaseWaitingReservationPort, never()).execute(any());
    }

    @Test
    @DisplayName("DB 실행이 실패하면 성공 offset을 저장하지 않는다")
    void doesNotAcknowledgeFailedDatabaseOperation() {
        when(purchaseWaitingReservationPort.execute(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> reservationKafkaConsumer.receive(record("1", json(1)), acknowledgment)).isInstanceOf(IllegalStateException.class);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("남은 실행 예산이 있으면 offset 대신 지연 재전달을 요청한다")
    void retriesWithoutCommittingOffset() {
        when(purchaseWaitingReservationPort.execute(any())).thenReturn(ReservationJobOutcome.RETRY);
        reservationKafkaConsumer.receive(record("1", json(1)), acknowledgment);
        verify(acknowledgment).nack(Duration.ofSeconds(1));
        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, String> record(String key, String payload) {
        return new ConsumerRecord<>("reservations", 0, 1, key, payload);
    }

    private String json(int version) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PurchaseReservationRequested\",\"schemaVersion\":" + version
            + ",\"occurredAt\":\"2026-10-19T00:00:00Z\",\"waitingEntryId\":\"" + UUID.randomUUID() + "\",\"saleProductId\":1,\"processingToken\":\"" + UUID.randomUUID() + "\"}";
    }
}
