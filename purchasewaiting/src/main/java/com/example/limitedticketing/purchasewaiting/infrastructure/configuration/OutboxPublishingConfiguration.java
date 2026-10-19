package com.example.limitedticketing.purchasewaiting.infrastructure.configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

@Configuration
public class OutboxPublishingConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService outboxCallbackExecutor(
        @Value("${limited-ticketing.kafka.outbox.callback-threads:2}") int threads,
        @Value("${limited-ticketing.kafka.outbox.callback-queue-capacity:10}") int queueCapacity) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
            new CustomizableThreadFactory("outbox-callback-"), new ThreadPoolExecutor.AbortPolicy());
    }
}
