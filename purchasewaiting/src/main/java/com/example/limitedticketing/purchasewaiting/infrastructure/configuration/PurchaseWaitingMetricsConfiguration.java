package com.example.limitedticketing.purchasewaiting.infrastructure.configuration;

import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging.ReservationKafkaConsumer;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.PurchaseWaitingJpaRepository;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.persistence.PurchaseWaitingOutboxJdbcRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

@Configuration
public class PurchaseWaitingMetricsConfiguration {
    @Bean
    public MeterBinder purchaseWaitingMetrics(PurchaseWaitingJpaRepository purchaseWaitingJpaRepository,
        PurchaseWaitingOutboxJdbcRepository purchaseWaitingOutboxJdbcRepository,
        ObjectProvider<KafkaListenerEndpointRegistry> listenerRegistry, Clock clock) {
        return registry -> {
            for (PurchaseWaitingStatus status : PurchaseWaitingStatus.values()) {
                Gauge.builder("reservation.waiting.entries", purchaseWaitingJpaRepository,
                    repository -> repository.countByStatusIn(List.of(status))).tag("status", status.name()).register(registry);
            }
            Gauge.builder("reservation.outbox.pending", purchaseWaitingOutboxJdbcRepository,
                PurchaseWaitingOutboxJdbcRepository::countPending).register(registry);
            Gauge.builder("reservation.outbox.oldest.seconds", purchaseWaitingOutboxJdbcRepository,
                repository -> repository.oldestPendingSeconds(LocalDateTime.now(clock))).register(registry);
            Gauge.builder("reservation.dispatched.oldest.seconds", purchaseWaitingJpaRepository,
                repository -> repository.oldestDispatchedSeconds(LocalDateTime.now(clock))).register(registry);
            Gauge.builder("reservation.execution.attempts", purchaseWaitingJpaRepository,
                PurchaseWaitingJpaRepository::sumExecutionAttempts).register(registry);
            Gauge.builder("reservation.consumer.running", listenerRegistry, provider -> {
                KafkaListenerEndpointRegistry endpoints = provider.getIfAvailable();
                if (endpoints == null || endpoints.getListenerContainer(ReservationKafkaConsumer.LISTENER_ID) == null) {
                    return 0;
                }
                return endpoints.getListenerContainer(ReservationKafkaConsumer.LISTENER_ID).isRunning() ? 1 : 0;
            }).register(registry);
        };
    }
}
