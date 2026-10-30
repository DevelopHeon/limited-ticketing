package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.infrastructure.metrics.ReservationMessagingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@DisplayName("Kafka 비동기 ACK 계약과 발행 지표")
class KafkaReservationMessagePublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final KafkaReservationMessagePublisher publisher = new KafkaReservationMessagePublisher(kafkaTemplate, new ReservationMessagingMetrics(meterRegistry));

    @Test
    @DisplayName("Kafka ACK 전에 성공 Future나 성공 지표를 확정하지 않는다")
    void futureCompletesOnlyAfterBrokerAcknowledgment() {
        CompletableFuture<SendResult<String, String>> kafkaAcknowledgment = new CompletableFuture<>();
        when(kafkaTemplate.send("topic", "1", "payload")).thenReturn(kafkaAcknowledgment);
        CompletableFuture<Void> result = publisher.publish(new ReservationOutboxMessage(UUID.randomUUID(), "topic", "1", "payload", 1));
        assertThat(result).isNotDone();
        assertThat(meterRegistry.get("reservation.outbox.publish").tag("result", "acknowledged").counter().count()).isZero();
        kafkaAcknowledgment.complete(mock(SendResult.class));
        assertThat(result).isCompletedWithValue(null);
        assertThat(meterRegistry.get("reservation.outbox.publish").tag("result", "acknowledged").counter().count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("send 동기 예외와 ACK 실패 모두 실패 Future와 실패 지표로 전달한다")
    void brokerErrorsRemainExceptional(boolean synchronous) {
        IllegalStateException failure = new IllegalStateException("broker unavailable");
        if (synchronous) {
            when(kafkaTemplate.send("topic", "1", "payload")).thenThrow(failure);
        } else {
            when(kafkaTemplate.send("topic", "1", "payload")).thenReturn(CompletableFuture.failedFuture(failure));
        }
        CompletableFuture<Void> result = publisher.publish(new ReservationOutboxMessage(UUID.randomUUID(), "topic", "1", "payload", 1));
        assertThatThrownBy(result::join).hasCause(failure);
        assertThat(meterRegistry.get("reservation.outbox.publish").tag("result", "failed").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("reservation.outbox.publish").tag("result", "acknowledged").counter().count()).isZero();
    }
}
