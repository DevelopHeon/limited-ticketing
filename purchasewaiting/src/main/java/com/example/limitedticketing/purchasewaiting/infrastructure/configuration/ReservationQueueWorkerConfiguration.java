package com.example.limitedticketing.purchasewaiting.infrastructure.configuration;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReservationQueueWorkerConfiguration {

    @Bean(name = "reservationQueueTaskExecutor")
    public Executor reservationQueueTaskExecutor(
        @Value("${limited-ticketing.queue.worker.max-concurrent-processing}") int maxConcurrentProcessing
    ) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(maxConcurrentProcessing);
        taskExecutor.setMaxPoolSize(maxConcurrentProcessing);
        taskExecutor.setQueueCapacity(maxConcurrentProcessing * 100);
        taskExecutor.setThreadNamePrefix("reservation-queue-");
        taskExecutor.initialize();
        return taskExecutor;
    }
}
