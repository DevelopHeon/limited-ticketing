package com.example.limitedticketing.sale.infrastructure.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.limitedticketing.sale.infrastructure.metrics.SaleCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 캐시 응답 계약과 DB 전환")
class RedisSaleProductReadCacheAdapterTest {
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    private SimpleMeterRegistry meterRegistry;
    private RedisSaleProductReadCacheAdapter redisSaleProductReadCacheAdapter;
    private final String generation = UUID.randomUUID().toString();

    @BeforeEach
    void prepareAdapter() {
        meterRegistry = new SimpleMeterRegistry();
        redisSaleProductReadCacheAdapter = new RedisSaleProductReadCacheAdapter(stringRedisTemplate,
            new SaleProductCacheCodec(new ObjectMapper()), new SaleCacheMetrics(meterRegistry), true, 30000, 10000, 2000, 10);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(generation);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @MethodSource("invalidResponses")
    @DisplayName("알 수 없거나 손상된 Lua 응답은 대기로 숨기지 않고 오류를 기록해 DB로 전환한다")
    void invalidResponseFallsBackWithoutTreatingItAsBusy(List<?> response) {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(response);

        assertThat(redisSaleProductReadCacheAdapter.findDetail(1L).status()).isEqualTo(CacheLookup.Status.BYPASS);
        assertThat(meterRegistry.get("sale.cache.operations").tags("operation", "read", "result", "error").counter().count())
            .isEqualTo(1);
        assertThat(meterRegistry.find("sale.cache.lookup").tag("result", "unexpected").counters()).isEmpty();
    }

    static Stream<Arguments> invalidResponses() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()), Arguments.of(List.of("UNEXPECTED")),
            Arguments.of(Arrays.asList((Object) null)), Arguments.of(List.of("HIT")),
            Arguments.of(List.of("CLAIMED", "invalid-generation", "token", "1000")));
    }

    @Test
    @DisplayName("generation 충돌 후 정상 소유권 응답을 받으면 내부 재시도 결과를 반환한다")
    void staleResponseRetriesBeforeReturningClaim() {
        String token = UUID.randomUUID().toString();
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of("STALE"), List.of("CLAIMED", generation, token, "1000"));

        CacheLookup<?> lookup = redisSaleProductReadCacheAdapter.findDetail(1L);

        assertThat(lookup.status()).isEqualTo(CacheLookup.Status.CLAIMED);
        assertThat(lookup.lease().generation()).isEqualTo(UUID.fromString(generation));
        assertThat(lookup.lease().token()).isEqualTo(UUID.fromString(token));
    }

    @Test
    @DisplayName("generation 충돌이 반복되면 제한된 시도 후 DB로 전환한다")
    void repeatedStaleResponsesFallBack() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of("STALE"));

        assertThat(redisSaleProductReadCacheAdapter.findDetail(1L).status()).isEqualTo(CacheLookup.Status.BYPASS);
        verify(stringRedisTemplate, times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
