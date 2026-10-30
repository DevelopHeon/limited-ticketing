package com.example.limitedticketing.purchasewaiting.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ReservationMessagingMetrics {
    private final Counter acknowledgedCounter;
    private final Counter failedCounter;
    private final Counter completedCounter;
    private final Counter retryCounter;
    private final Counter rejectedCounter;
    private final Timer executionTimer;

    public ReservationMessagingMetrics(MeterRegistry meterRegistry) {
        acknowledgedCounter = meterRegistry.counter("reservation.outbox.publish", "result", "acknowledged");
        failedCounter = meterRegistry.counter("reservation.outbox.publish", "result", "failed");
        completedCounter = meterRegistry.counter("reservation.consumer.records", "outcome", "completed");
        retryCounter = meterRegistry.counter("reservation.consumer.records", "outcome", "retry");
        rejectedCounter = meterRegistry.counter("reservation.consumer.records", "outcome", "rejected");
        executionTimer = meterRegistry.timer("reservation.consumer.duration");
    }

    public void recordPublishAcknowledged() {
        acknowledgedCounter.increment();
    }

    public void recordPublishFailed() {
        failedCounter.increment();
    }

    public void recordConsumerCompleted() {
        completedCounter.increment();
    }

    public void recordConsumerRetry() {
        retryCounter.increment();
    }

    public void recordConsumerRejected() {
        rejectedCounter.increment();
    }

    public <T> T measureExecution(Supplier<T> execution) {
        return executionTimer.record(execution);
    }
}
