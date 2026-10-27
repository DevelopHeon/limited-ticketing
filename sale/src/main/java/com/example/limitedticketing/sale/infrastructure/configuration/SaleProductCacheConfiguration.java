package com.example.limitedticketing.sale.infrastructure.configuration;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class SaleProductCacheConfiguration {
    @Bean
    public MeterBinder saleCacheMetrics(@Value("${limited-ticketing.sale-cache.enabled:true}") boolean enabled) {
        return registry -> Gauge.builder("sale.cache.enabled", () -> enabled ? 1 : 0).register(registry);
    }

    @Bean(defaultCandidate = false)
    public LettuceConnectionFactory saleCacheRedisConnectionFactory(
        @Value("${limited-ticketing.sale-cache.redis.host:localhost}") String host,
        @Value("${limited-ticketing.sale-cache.redis.port:6380}") int port,
        @Value("${limited-ticketing.sale-cache.redis.connect-timeout-ms:200}") long connectTimeoutMillis,
        @Value("${limited-ticketing.sale-cache.redis.command-timeout-ms:100}") long commandTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || commandTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Cache Redis timeouts must be positive");
        }
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(commandTimeoutMillis))
            .clientOptions(ClientOptions.builder().socketOptions(SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis)).build()).build())
            .build();
        return new LettuceConnectionFactory(server, client);
    }

    @Bean(defaultCandidate = false)
    public StringRedisTemplate saleCacheStringRedisTemplate(
        @Qualifier("saleCacheRedisConnectionFactory") LettuceConnectionFactory saleCacheRedisConnectionFactory) {
        return new StringRedisTemplate(saleCacheRedisConnectionFactory);
    }
}
