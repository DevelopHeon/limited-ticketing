package com.example.limitedticketing.purchasewaiting.infrastructure.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.QueueSequenceGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisQueueSequenceGenerator implements QueueSequenceGenerator {

    private final StringRedisTemplate stringRedisTemplate;
    private final ReservationQueueRedisKeys keys;

    public RedisQueueSequenceGenerator(
        StringRedisTemplate stringRedisTemplate,
        ReservationQueueRedisKeys keys
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keys = keys;
    }

    @Override
    public long nextSequence(Long saleProductId) {
        Long sequence = stringRedisTemplate.opsForValue().increment(keys.sequence(saleProductId));
        if (sequence == null) {
            throw new IllegalStateException("Unable to generate queue sequence");
        }
        return sequence;
    }
}
