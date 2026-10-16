package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueueRecoveryPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPurchaseWaitingQueueRecoveryAdapter implements PurchaseWaitingQueueRecoveryPort {

    private final StringRedisTemplate stringRedisTemplate;
    private final PurchaseWaitingRedisKeys purchaseWaitingRedisKeys;

    public RedisPurchaseWaitingQueueRecoveryAdapter(
        StringRedisTemplate stringRedisTemplate,
        PurchaseWaitingRedisKeys purchaseWaitingRedisKeys
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.purchaseWaitingRedisKeys = purchaseWaitingRedisKeys;
    }

    @Override
    public Set<Long> findSaleProductIds() {
        Set<Long> saleProductIds = new HashSet<>();
        collectSaleProductIds(purchaseWaitingRedisKeys.waitingPattern(), saleProductIds);
        collectSaleProductIds(purchaseWaitingRedisKeys.processingPattern(), saleProductIds);
        collectSaleProductIds(purchaseWaitingRedisKeys.sequencePattern(), saleProductIds);
        collectSaleProductIds(purchaseWaitingRedisKeys.tokensPattern(), saleProductIds);
        return saleProductIds;
    }

    @Override
    public Set<UUID> findWaitingEntryIds(Long saleProductId) {
        return findEntryIds(purchaseWaitingRedisKeys.waiting(saleProductId));
    }

    @Override
    public Set<UUID> findProcessingEntryIds(Long saleProductId) {
        return findEntryIds(purchaseWaitingRedisKeys.processing(saleProductId));
    }

    @Override
    public void removeWaiting(Long saleProductId, Collection<UUID> queueEntryIds) {
        remove(purchaseWaitingRedisKeys.waiting(saleProductId), queueEntryIds);
    }

    @Override
    public void removeProcessing(Long saleProductId, Collection<UUID> queueEntryIds) {
        remove(purchaseWaitingRedisKeys.processing(saleProductId), queueEntryIds);
        if (!queueEntryIds.isEmpty()) {
            stringRedisTemplate.opsForHash().delete(purchaseWaitingRedisKeys.tokens(saleProductId),
                queueEntryIds.stream().map(UUID::toString).toArray(Object[]::new));
        }
    }

    @Override
    public void delete(Long saleProductId) {
        stringRedisTemplate.delete(List.of(
            purchaseWaitingRedisKeys.waiting(saleProductId),
            purchaseWaitingRedisKeys.processing(saleProductId),
            purchaseWaitingRedisKeys.sequence(saleProductId),
            purchaseWaitingRedisKeys.tokens(saleProductId)
        ));
    }

    private void collectSaleProductIds(String pattern, Set<Long> saleProductIds) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1_000).build();
        try (Cursor<String> matchingKeys = stringRedisTemplate.scan(options)) {
            matchingKeys.forEachRemaining(key -> saleProductIds.add(purchaseWaitingRedisKeys.saleProductIdOf(key)));
        }
    }

    private Set<UUID> findEntryIds(String key) {
        Set<String> entries = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        return entries.stream().map(UUID::fromString).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void remove(String key, Collection<UUID> queueEntryIds) {
        if (queueEntryIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(
            key,
            queueEntryIds.stream().map(UUID::toString).toArray(Object[]::new)
        );
    }
}
