package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.execution;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingTaskExecutionPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ThreadPoolPurchaseWaitingTaskAdapter implements PurchaseWaitingTaskExecutionPort, DisposableBean {

    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final int maxThreads;

    public ThreadPoolPurchaseWaitingTaskAdapter(MeterRegistry meterRegistry,
        @Value("${limited-ticketing.queue.worker.global-concurrent-processing:10}") int maxThreads) {
        this.maxThreads = maxThreads;
        threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(maxThreads);
        threadPoolTaskExecutor.setMaxPoolSize(maxThreads);
        threadPoolTaskExecutor.setQueueCapacity(0);
        threadPoolTaskExecutor.setThreadNamePrefix("reservation-queue-");
        threadPoolTaskExecutor.initialize();
        new ExecutorServiceMetrics(threadPoolTaskExecutor.getThreadPoolExecutor(), "reservation.queue", List.of()).bindTo(meterRegistry);
    }

    @Override
    public int availableSlots() {
        return Math.max(0, maxThreads - threadPoolTaskExecutor.getActiveCount());
    }

    @Override
    public void execute(Runnable task) {
        threadPoolTaskExecutor.execute(task);
    }

    @Override
    public void destroy() {
        threadPoolTaskExecutor.shutdown();
    }
}
