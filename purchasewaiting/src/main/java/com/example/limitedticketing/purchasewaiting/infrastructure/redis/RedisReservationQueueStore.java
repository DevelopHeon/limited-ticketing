package com.example.limitedticketing.purchasewaiting.infrastructure.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueClaim;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisReservationQueueStore implements ReservationQueueStore {

    private static final RedisScript<List> CLAIM = RedisScript.of(new ClassPathResource("redis/queue-claim.lua"), List.class);
    private static final RedisScript<Long> RELEASE = RedisScript.of(new ClassPathResource("redis/queue-release.lua"), Long.class);
    private static final RedisScript<Long> ENQUEUE = RedisScript.of(new ClassPathResource("redis/queue-enqueue.lua"), Long.class);
    private static final RedisScript<Long> REQUEUE = RedisScript.of(new ClassPathResource("redis/queue-requeue.lua"), Long.class);
    private static final RedisScript<Long> RESTORE = RedisScript.of(new ClassPathResource("redis/queue-restore-waiting.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final ReservationQueueRedisKeys keys;

    public RedisReservationQueueStore(StringRedisTemplate redis, ReservationQueueRedisKeys keys) {
        this.redis = redis;
        this.keys = keys;
    }

    @Override
    public void enqueue(ReservationQueueEntry entry) {
        redis.execute(ENQUEUE, List.of(keys.waiting(entry.getSaleProductId()), keys.processing(entry.getSaleProductId())),
            entry.getId().toString(), Long.toString(entry.getQueueSequence()));
    }

    @Override
    public List<ReservationQueueClaim> claimNext(Long saleProductId, int maxConcurrentProcessing, int claimBatchSize, Instant claimedAt) {
        List<?> result = redis.execute(CLAIM,
            List.of(keys.waiting(saleProductId), keys.processing(saleProductId), keys.tokens(saleProductId)),
            Integer.toString(maxConcurrentProcessing), Integer.toString(claimBatchSize),
            Long.toString(claimedAt.toEpochMilli()), UUID.randomUUID().toString());
        List<ReservationQueueClaim> claims = new ArrayList<>();
        for (int index = 0; index < result.size(); index += 2) {
            claims.add(new ReservationQueueClaim(UUID.fromString(result.get(index).toString()),
                UUID.fromString(result.get(index + 1).toString())));
        }
        return claims;
    }

    @Override
    public void releaseProcessing(ReservationQueueEntry entry) {
        releaseProcessing(entry.getSaleProductId(), entry.getId(), entry.getProcessingToken());
    }

    @Override
    public void releaseProcessing(Long saleProductId, UUID queueEntryId, UUID token) {
        redis.execute(RELEASE, List.of(keys.processing(saleProductId), keys.tokens(saleProductId)),
            queueEntryId.toString(), token.toString());
    }

    @Override
    public void requeue(ReservationQueueEntry entry, UUID previousToken) {
        redis.execute(REQUEUE, List.of(keys.waiting(entry.getSaleProductId()), keys.processing(entry.getSaleProductId()),
                keys.tokens(entry.getSaleProductId())), entry.getId().toString(), previousToken.toString(),
            Long.toString(entry.getQueueSequence()));
    }

    @Override
    public void restoreWaiting(ReservationQueueEntry entry) {
        redis.execute(RESTORE, List.of(keys.waiting(entry.getSaleProductId()), keys.processing(entry.getSaleProductId()),
                keys.tokens(entry.getSaleProductId())), entry.getId().toString(), Long.toString(entry.getQueueSequence()));
    }

    @Override
    public boolean contains(Long saleProductId, UUID queueEntryId) {
        return redis.opsForZSet().rank(keys.waiting(saleProductId), queueEntryId.toString()) != null
            || redis.opsForZSet().rank(keys.processing(saleProductId), queueEntryId.toString()) != null;
    }

    @Override
    public OptionalLong findPosition(Long saleProductId, UUID queueEntryId) {
        try {
            Long rank = redis.opsForZSet().rank(keys.waiting(saleProductId), queueEntryId.toString());
            return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
        } catch (DataAccessException exception) {
            throw ReservationQueueException.unavailable();
        }
    }
}
