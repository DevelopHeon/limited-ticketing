package com.example.limitedticketing.purchasewaiting.infrastructure.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisReservationQueueStore implements ReservationQueueStore {

    private static final DefaultRedisScript<List> CLAIM_NEXT_SCRIPT = new DefaultRedisScript<>("""
        local maxConcurrent = tonumber(ARGV[1])
        local batchSize = tonumber(ARGV[2])
        local processingCount = redis.call('ZCARD', KEYS[2])
        local available = maxConcurrent - processingCount
        if available <= 0 then
            return {}
        end

        local claimCount = math.min(available, batchSize)
        local entries = redis.call('ZRANGE', KEYS[1], 0, claimCount - 1)
        for _, entryId in ipairs(entries) do
            redis.call('ZREM', KEYS[1], entryId)
            redis.call('ZADD', KEYS[2], ARGV[3], entryId)
        end
        return entries
        """, List.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ReservationQueueRedisKeys keys;

    public RedisReservationQueueStore(
        StringRedisTemplate stringRedisTemplate,
        ReservationQueueRedisKeys keys
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keys = keys;
    }

    @Override
    public void enqueue(ReservationQueueEntry entry) {
        stringRedisTemplate.opsForZSet().add(
            keys.waiting(entry.getSaleProductId()),
            entry.getId().toString(),
            entry.getQueueSequence()
        );
    }

    @Override
    public List<UUID> claimNext(Long saleProductId, int maxConcurrentProcessing, int claimBatchSize, Instant claimedAt) {
        List<?> claimedEntries = stringRedisTemplate.execute(
            CLAIM_NEXT_SCRIPT,
            List.of(keys.waiting(saleProductId), keys.processing(saleProductId)),
            Integer.toString(maxConcurrentProcessing),
            Integer.toString(claimBatchSize),
            Long.toString(claimedAt.toEpochMilli())
        );

        return claimedEntries.stream()
            .map(Object::toString)
            .map(UUID::fromString)
            .toList();
    }

    @Override
    public void releaseProcessing(ReservationQueueEntry entry) {
        releaseProcessing(entry.getSaleProductId(), entry.getId());
    }

    @Override
    public void releaseProcessing(Long saleProductId, UUID queueEntryId) {
        stringRedisTemplate.opsForZSet().remove(keys.processing(saleProductId), queueEntryId.toString());
    }

    @Override
    public void requeue(ReservationQueueEntry entry) {
        stringRedisTemplate.opsForZSet().remove(keys.processing(entry.getSaleProductId()), entry.getId().toString());
        enqueue(entry);
    }

    @Override
    public boolean contains(Long saleProductId, UUID queueEntryId) {
        String entryId = queueEntryId.toString();
        return stringRedisTemplate.opsForZSet().rank(keys.waiting(saleProductId), entryId) != null
            || stringRedisTemplate.opsForZSet().rank(keys.processing(saleProductId), entryId) != null;
    }

    @Override
    public OptionalLong findPosition(Long saleProductId, UUID queueEntryId) {
        Long rank = stringRedisTemplate.opsForZSet().rank(keys.waiting(saleProductId), queueEntryId.toString());
        return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
    }
}
