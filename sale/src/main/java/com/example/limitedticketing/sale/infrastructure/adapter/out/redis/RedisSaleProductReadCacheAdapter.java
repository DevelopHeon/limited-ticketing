package com.example.limitedticketing.sale.infrastructure.adapter.out.redis;

import com.example.limitedticketing.sale.application.port.out.SaleProductReadCachePort;
import com.example.limitedticketing.sale.application.port.out.dto.CacheFillLease;
import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductCacheQuery;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductDetail;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.limitedticketing.sale.infrastructure.metrics.SaleCacheMetrics;
import com.example.limitedticketing.sale.infrastructure.metrics.SaleCacheMetrics.CacheKind;
import com.example.limitedticketing.sale.infrastructure.metrics.SaleCacheMetrics.Operation;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisSaleProductReadCacheAdapter implements SaleProductReadCachePort {
    private static final Logger LOG = LoggerFactory.getLogger(RedisSaleProductReadCacheAdapter.class);
    private static final String PREFIX = "sale-cache:{catalog}:v1:";
    private static final String GENERATION_KEY = PREFIX + "generation";
    private static final RedisScript<List> READ_CLAIM = RedisScript.of(
        new ClassPathResource("redis/sale-cache-read-claim.lua"), List.class);
    private static final RedisScript<Long> STORE = RedisScript.of(
        new ClassPathResource("redis/sale-cache-store.lua"), Long.class);
    private static final RedisScript<Long> DELETE_IF_VALUE = RedisScript.of(
        new ClassPathResource("redis/sale-cache-delete-if-value.lua"), Long.class);
    private final StringRedisTemplate saleCacheStringRedisTemplate;
    private final SaleProductCacheCodec saleProductCacheCodec;
    private final SaleCacheMetrics saleCacheMetrics;
    private final boolean enabled;
    private final long detailTtlMillis;
    private final long listTtlMillis;
    private final long lockLeaseMillis;
    private final int jitterPercent;

    public RedisSaleProductReadCacheAdapter(
        @Qualifier("saleCacheStringRedisTemplate") StringRedisTemplate saleCacheStringRedisTemplate,
        SaleProductCacheCodec saleProductCacheCodec, SaleCacheMetrics saleCacheMetrics,
        @Value("${limited-ticketing.sale-cache.enabled:true}") boolean enabled,
        @Value("${limited-ticketing.sale-cache.detail-ttl-ms:30000}") long detailTtlMillis,
        @Value("${limited-ticketing.sale-cache.list-ttl-ms:10000}") long listTtlMillis,
        @Value("${limited-ticketing.sale-cache.lock-lease-ms:2000}") long lockLeaseMillis,
        @Value("${limited-ticketing.sale-cache.jitter-percent:10}") int jitterPercent) {
        if (detailTtlMillis <= 0 || listTtlMillis <= 0 || lockLeaseMillis <= 0 || jitterPercent < 0 || jitterPercent > 90) {
            throw new IllegalArgumentException("Cache TTL and Lease must be positive; jitter must be between 0 and 90");
        }
        this.saleCacheStringRedisTemplate = saleCacheStringRedisTemplate;
        this.saleProductCacheCodec = saleProductCacheCodec;
        this.saleCacheMetrics = saleCacheMetrics;
        this.enabled = enabled;
        this.detailTtlMillis = detailTtlMillis;
        this.listTtlMillis = listTtlMillis;
        this.lockLeaseMillis = lockLeaseMillis;
        this.jitterPercent = jitterPercent;
    }

    @Override
    public CacheLookup<CachedSaleProductDetail> findDetail(Long saleProductId) {
        return read(CacheKind.DETAIL, "detail:" + saleProductId, detailTtlMillis,
            payload -> saleProductCacheCodec.decodeDetail(payload, saleProductId));
    }

    @Override
    public CacheLookup<CachedSaleProductPage> findPage(SaleProductCacheQuery query) {
        return read(CacheKind.LIST, pageSuffix(query), listTtlMillis,
            payload -> saleProductCacheCodec.decodePage(payload, query.page(), query.size()));
    }

    @Override
    public void storeDetail(Long saleProductId, CacheFillLease lease, CachedSaleProductDetail result) {
        store(CacheKind.DETAIL, "detail:" + saleProductId, lease, () -> saleProductCacheCodec.encodeDetail(result));
    }

    @Override
    public void storePage(SaleProductCacheQuery query, CacheFillLease lease, CachedSaleProductPage result) {
        store(CacheKind.LIST, pageSuffix(query), lease, () -> saleProductCacheCodec.encodePage(result));
    }

    @Override
    public void releaseDetail(Long saleProductId, CacheFillLease lease) {
        release("detail:" + saleProductId, lease);
    }

    @Override
    public void releasePage(SaleProductCacheQuery query, CacheFillLease lease) {
        release(pageSuffix(query), lease);
    }

    @Override
    public void invalidate() {
        if (!enabled) {
            return;
        }
        try {
            saleCacheStringRedisTemplate.opsForValue().set(GENERATION_KEY, UUID.randomUUID().toString());
            saleCacheMetrics.recordInvalidation();
        } catch (DataAccessException exception) {
            saleCacheMetrics.recordError(Operation.INVALIDATE);
            LOG.warn("판매 캐시 무효화 실패: 기존 TTL로 복구", exception);
        }
    }

    private <T> CacheLookup<T> read(CacheKind kind, String suffix, long ttlMillis, Decoder<T> decoder) {
        if (!enabled) {
            return CacheLookup.bypass();
        }
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                String generation = generation();
                String token = UUID.randomUUID().toString();
                String key = key(generation, suffix);
                List<?> result = saleCacheStringRedisTemplate.execute(READ_CLAIM,
                    List.of(GENERATION_KEY, key, key + ":lock"), generation, token,
                    Long.toString(lockLeaseMillis), Long.toString(jittered(ttlMillis)));
                RedisCacheReadStatus status = parseReadStatus(result);
                switch (status) {
                    case STALE -> {
                    }
                    case HIT -> {
                        saleCacheMetrics.recordLookup(kind, CacheLookup.Status.HIT);
                        String payload = result.get(1).toString();
                        try {
                            return CacheLookup.hit(decoder.decode(payload));
                        } catch (JsonProcessingException | IllegalArgumentException exception) {
                            saleCacheStringRedisTemplate.execute(DELETE_IF_VALUE, List.of(key), payload);
                            saleCacheMetrics.recordError(Operation.DECODE);
                            return CacheLookup.bypass();
                        }
                    }
                    case CLAIMED -> {
                        saleCacheMetrics.recordLookup(kind, CacheLookup.Status.CLAIMED);
                        return CacheLookup.claimed(new CacheFillLease(UUID.fromString(result.get(1).toString()),
                            UUID.fromString(result.get(2).toString()), Long.parseLong(result.get(3).toString())));
                    }
                    case BUSY -> {
                        saleCacheMetrics.recordLookup(kind, CacheLookup.Status.BUSY);
                        return CacheLookup.busy();
                    }
                }
            }
            return CacheLookup.bypass();
        } catch (DataAccessException | IllegalArgumentException exception) {
            saleCacheMetrics.recordError(Operation.READ);
            LOG.debug("판매 캐시 조회 실패: DB 조회로 전환", exception);
            return CacheLookup.bypass();
        }
    }

    private void store(CacheKind kind, String suffix, CacheFillLease lease, Encoder encoder) {
        try {
            String key = key(lease.generation().toString(), suffix);
            Long stored = saleCacheStringRedisTemplate.execute(STORE, List.of(GENERATION_KEY, key, key + ":lock"),
                lease.generation().toString(), lease.token().toString(), Long.toString(lease.freshnessDeadlineEpochMillis()), encoder.encode());
            saleCacheMetrics.recordWrite(kind, Long.valueOf(1).equals(stored));
        } catch (DataAccessException | JsonProcessingException exception) {
            saleCacheMetrics.recordError(Operation.STORE);
            LOG.debug("판매 캐시 저장 실패: DB 결과 유지", exception);
        }
    }

    private void release(String suffix, CacheFillLease lease) {
        try {
            saleCacheStringRedisTemplate.execute(DELETE_IF_VALUE,
                List.of(key(lease.generation().toString(), suffix) + ":lock"), lease.token().toString());
        } catch (DataAccessException exception) {
            saleCacheMetrics.recordError(Operation.RELEASE);
            LOG.debug("판매 캐시 lock 반환 실패: Lease 만료로 복구", exception);
        }
    }

    private String generation() {
        String generation = saleCacheStringRedisTemplate.opsForValue().get(GENERATION_KEY);
        if (generation != null) {
            try {
                if (!UUID.fromString(generation).toString().equals(generation)) {
                    throw new IllegalArgumentException("Non-canonical cache generation");
                }
            } catch (IllegalArgumentException exception) {
                saleCacheStringRedisTemplate.execute(DELETE_IF_VALUE, List.of(GENERATION_KEY), generation);
                saleCacheMetrics.recordError(Operation.DECODE);
                generation = null;
            }
        }
        if (generation == null) {
            saleCacheStringRedisTemplate.opsForValue().setIfAbsent(GENERATION_KEY, UUID.randomUUID().toString());
            generation = saleCacheStringRedisTemplate.opsForValue().get(GENERATION_KEY);
        }
        return generation == null ? UUID.randomUUID().toString() : generation;
    }

    private long jittered(long ttlMillis) {
        long minimum = Math.max(1, ttlMillis * (100 - jitterPercent) / 100);
        return minimum == ttlMillis ? ttlMillis : ThreadLocalRandom.current().nextLong(minimum, ttlMillis + 1);
    }

    private String pageSuffix(SaleProductCacheQuery query) {
        return "list:" + (query.status() == null ? "ALL" : query.status().name()) + ":" + query.page() + ":"
            + query.size() + ":" + query.sort().name() + ":" + query.direction().name();
    }

    private String key(String generation, String suffix) {
        return PREFIX + generation + ":" + suffix;
    }

    private RedisCacheReadStatus parseReadStatus(List<?> response) {
        if (response == null || response.isEmpty() || response.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("Missing or invalid cache script response");
        }
        RedisCacheReadStatus status = RedisCacheReadStatus.valueOf((String) response.get(0));
        int expectedSize = switch (status) {
            case HIT -> 2;
            case CLAIMED -> 4;
            case BUSY, STALE -> 1;
        };
        if (response.size() != expectedSize) {
            throw new IllegalArgumentException("Invalid cache script response size");
        }
        return status;
    }

    private enum RedisCacheReadStatus {
        HIT, CLAIMED, BUSY, STALE
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(String payload) throws JsonProcessingException;
    }

    @FunctionalInterface
    private interface Encoder {
        String encode() throws JsonProcessingException;
    }
}
