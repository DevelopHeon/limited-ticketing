package com.example.limitedticketing.purchasewaiting.infrastructure.executor;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class BoundedReservationQueueTaskExecutor implements ReservationQueueTaskExecutor, DisposableBean {

    private final ThreadPoolTaskExecutor executor;
    private final int maxThreads;

    public BoundedReservationQueueTaskExecutor(MeterRegistry registry,
        @Value("${limited-ticketing.queue.worker.global-concurrent-processing:10}") int maxThreads) {
        this.maxThreads = maxThreads;
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxThreads);
        executor.setMaxPoolSize(maxThreads);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("reservation-queue-");
        executor.initialize();
        new ExecutorServiceMetrics(executor.getThreadPoolExecutor(), "reservation.queue", List.of()).bindTo(registry);
    }

    @Override
    public int availableSlots() {
        return Math.max(0, maxThreads - executor.getActiveCount());
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
