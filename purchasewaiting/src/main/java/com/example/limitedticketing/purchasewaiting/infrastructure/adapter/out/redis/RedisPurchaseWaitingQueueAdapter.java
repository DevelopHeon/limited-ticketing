package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingClaim;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
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
public class RedisPurchaseWaitingQueueAdapter implements PurchaseWaitingQueuePort {

    private static final RedisScript<List> CLAIM = RedisScript.of(new ClassPathResource("redis/queue-claim.lua"), List.class);
    private static final RedisScript<Long> RELEASE = RedisScript.of(new ClassPathResource("redis/queue-release.lua"), Long.class);
    private static final RedisScript<Long> ENQUEUE = RedisScript.of(new ClassPathResource("redis/queue-enqueue.lua"), Long.class);
    private static final RedisScript<Long> REQUEUE = RedisScript.of(new ClassPathResource("redis/queue-requeue.lua"), Long.class);
    private static final RedisScript<Long> RESTORE = RedisScript.of(new ClassPathResource("redis/queue-restore-waiting.lua"), Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final PurchaseWaitingRedisKeys purchaseWaitingRedisKeys;

    public RedisPurchaseWaitingQueueAdapter(StringRedisTemplate stringRedisTemplate, PurchaseWaitingRedisKeys purchaseWaitingRedisKeys) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.purchaseWaitingRedisKeys = purchaseWaitingRedisKeys;
    }

    @Override
    public void enqueue(PurchaseWaitingEntry entry) {
        stringRedisTemplate.execute(ENQUEUE, List.of(purchaseWaitingRedisKeys.waiting(entry.getSaleProductId()), purchaseWaitingRedisKeys.processing(entry.getSaleProductId())),
            entry.getId().toString(), Long.toString(entry.getQueueSequence()));
    }

    @Override
    public List<PurchaseWaitingClaim> claimNext(Long saleProductId, int maxConcurrentProcessing, int claimBatchSize, Instant claimedAt) {
        List<?> result = stringRedisTemplate.execute(CLAIM,
            List.of(purchaseWaitingRedisKeys.waiting(saleProductId), purchaseWaitingRedisKeys.processing(saleProductId), purchaseWaitingRedisKeys.tokens(saleProductId)),
            Integer.toString(maxConcurrentProcessing), Integer.toString(claimBatchSize),
            Long.toString(claimedAt.toEpochMilli()), UUID.randomUUID().toString());
        List<PurchaseWaitingClaim> claims = new ArrayList<>();
        for (int index = 0; index < result.size(); index += 2) {
            claims.add(new PurchaseWaitingClaim(UUID.fromString(result.get(index).toString()),
                UUID.fromString(result.get(index + 1).toString())));
        }
        return claims;
    }

    @Override
    public void releaseProcessing(PurchaseWaitingEntry entry) {
        releaseProcessing(entry.getSaleProductId(), entry.getId(), entry.getProcessingToken());
    }

    @Override
    public void releaseProcessing(Long saleProductId, UUID queueEntryId, UUID token) {
        stringRedisTemplate.execute(RELEASE, List.of(purchaseWaitingRedisKeys.processing(saleProductId), purchaseWaitingRedisKeys.tokens(saleProductId)),
            queueEntryId.toString(), token.toString());
    }

    @Override
    public void requeue(PurchaseWaitingEntry entry, UUID previousToken) {
        stringRedisTemplate.execute(REQUEUE, List.of(purchaseWaitingRedisKeys.waiting(entry.getSaleProductId()), purchaseWaitingRedisKeys.processing(entry.getSaleProductId()),
                purchaseWaitingRedisKeys.tokens(entry.getSaleProductId())), entry.getId().toString(), previousToken.toString(),
            Long.toString(entry.getQueueSequence()));
    }

    @Override
    public void restoreWaiting(PurchaseWaitingEntry entry) {
        stringRedisTemplate.execute(RESTORE, List.of(purchaseWaitingRedisKeys.waiting(entry.getSaleProductId()), purchaseWaitingRedisKeys.processing(entry.getSaleProductId()),
                purchaseWaitingRedisKeys.tokens(entry.getSaleProductId())), entry.getId().toString(), Long.toString(entry.getQueueSequence()));
    }

    @Override
    public boolean contains(Long saleProductId, UUID queueEntryId) {
        return stringRedisTemplate.opsForZSet().rank(purchaseWaitingRedisKeys.waiting(saleProductId), queueEntryId.toString()) != null
            || stringRedisTemplate.opsForZSet().rank(purchaseWaitingRedisKeys.processing(saleProductId), queueEntryId.toString()) != null;
    }

    @Override
    public OptionalLong findPosition(Long saleProductId, UUID queueEntryId) {
        try {
            Long rank = stringRedisTemplate.opsForZSet().rank(purchaseWaitingRedisKeys.waiting(saleProductId), queueEntryId.toString());
            return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
        } catch (DataAccessException exception) {
            throw PurchaseWaitingException.unavailable();
        }
    }
}
