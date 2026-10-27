package com.example.limitedticketing.configuration.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.data.redis.RedisHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration(proxyBeanMethods = false)
public class RedisHealthConfiguration {
    @Bean
    public RedisHealthIndicator redisHealthContributor(
        @Qualifier("redisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
        // 조회 캐시는 DB로 전환할 수 있으므로 필수 대기열 Redis만 health에 반영한다.
        return new RedisHealthIndicator(redisConnectionFactory);
    }
}
