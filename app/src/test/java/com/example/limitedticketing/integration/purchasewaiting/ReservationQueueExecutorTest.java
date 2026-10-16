package com.example.limitedticketing.integration.purchasewaiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.limitedticketing.purchasewaiting.infrastructure.executor.BoundedReservationQueueTaskExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("구매 대기 Worker 실행 용량 검증")
class ReservationQueueExecutorTest {

    @Test
    @DisplayName("실행 슬롯을 초과한 작업을 내부 큐에 쌓지 않고 즉시 거부하며 완료 후 슬롯을 반환한다")
    void rejectsOverflowWithoutBufferingAndReleasesFinishedSlots() throws Exception {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        BoundedReservationQueueTaskExecutor executor = new BoundedReservationQueueTaskExecutor(metrics, 2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int index = 0; index < 2; index++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.availableSlots()).isZero();
            assertThatThrownBy(() -> executor.execute(() -> { throw new AssertionError("Overflow task ran"); }))
                .isInstanceOf(RejectedExecutionException.class);
            assertThat(metrics.get("executor.queued").tag("name", "reservation.queue").gauge().value()).isZero();
            release.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(executor.availableSlots()).isEqualTo(2));
        } finally {
            release.countDown();
            executor.destroy();
            metrics.close();
        }
    }
}
