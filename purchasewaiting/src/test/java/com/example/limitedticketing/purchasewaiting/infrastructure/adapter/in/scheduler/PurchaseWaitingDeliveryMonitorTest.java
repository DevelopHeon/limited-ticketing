package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("실행을 시작하지 못한 전달 대기의 운영 경고")
class PurchaseWaitingDeliveryMonitorTest {
    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(ints = {59, 60})
    @DisplayName("전달 대기 시간이 60초에 도달한 경우에만 경고한다")
    void warningStartsAtSixtySeconds(int seconds, CapturedOutput output) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            AtomicInteger age = new AtomicInteger(seconds);
            registry.gauge("reservation.dispatched.oldest.seconds", age);
            new PurchaseWaitingDeliveryMonitor(registry).warnDelayedDeliveries();
            if (seconds >= 60) {
                assertThat(output).contains("Reservation delivery has waited at least 60 seconds");
            } else {
                assertThat(output).doesNotContain("Reservation delivery has waited at least 60 seconds");
            }
        } finally {
            registry.close();
        }
    }
}
