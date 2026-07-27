package com.example.limitedticketing.queue.infrastructure.redis;

import com.example.limitedticketing.queue.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.queue.domain.model.ReservationQueueEntry;
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

    public RedisReservationQueueStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public long nextSequence(Long saleProductId) {
        Long sequence = stringRedisTemplate.opsForValue().increment(sequenceKey(saleProductId));
        if (sequence == null) {
            throw new IllegalStateException("Unable to generate queue sequence");
        }
        return sequence;
    }

    @Override
    public void enqueue(ReservationQueueEntry entry) {
        stringRedisTemplate.opsForZSet().add(
            waitingKey(entry.getSaleProductId()),
            entry.getId().toString(),
            entry.getQueueSequence()
        );
    }

    @Override
    public List<UUID> claimNext(Long saleProductId, int maxConcurrentProcessing, int claimBatchSize, Instant claimedAt) {
        List<?> claimedEntries = stringRedisTemplate.execute(
            CLAIM_NEXT_SCRIPT,
            List.of(waitingKey(saleProductId), processingKey(saleProductId)),
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
        stringRedisTemplate.opsForZSet().remove(processingKey(saleProductId), queueEntryId.toString());
    }

    @Override
    public void requeue(ReservationQueueEntry entry) {
        stringRedisTemplate.opsForZSet().remove(processingKey(entry.getSaleProductId()), entry.getId().toString());
        enqueue(entry);
    }

    @Override
    public boolean contains(Long saleProductId, UUID queueEntryId) {
        String entryId = queueEntryId.toString();
        return stringRedisTemplate.opsForZSet().rank(waitingKey(saleProductId), entryId) != null
            || stringRedisTemplate.opsForZSet().rank(processingKey(saleProductId), entryId) != null;
    }

    @Override
    public void removeProcessingOlderThan(Long saleProductId, Instant threshold) {
        stringRedisTemplate.opsForZSet().removeRangeByScore(
            processingKey(saleProductId),
            Double.NEGATIVE_INFINITY,
            threshold.toEpochMilli()
        );
    }

    @Override
    public OptionalLong findPosition(Long saleProductId, UUID queueEntryId) {
        Long rank = stringRedisTemplate.opsForZSet().rank(waitingKey(saleProductId), queueEntryId.toString());
        return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
    }

    private String sequenceKey(Long saleProductId) {
        return "sequence:sale:" + saleProductId;
    }

    private String waitingKey(Long saleProductId) {
        return "waiting:sale:" + saleProductId;
    }

    private String processingKey(Long saleProductId) {
        return "processing:sale:" + saleProductId;
    }
}
