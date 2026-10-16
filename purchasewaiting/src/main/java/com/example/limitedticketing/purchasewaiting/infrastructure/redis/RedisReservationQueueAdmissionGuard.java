package com.example.limitedticketing.purchasewaiting.infrastructure.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueAdmissionGuard;
import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisReservationQueueAdmissionGuard implements ReservationQueueAdmissionGuard {

    private final StringRedisTemplate redis;

    public RedisReservationQueueAdmissionGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void check(Long memberId, Long saleProductId) {
        boolean admitted;
        try {
            admitted = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                "admission:sale:" + saleProductId + ":member:" + memberId, "1", Duration.ofSeconds(1)));
        } catch (DataAccessException exception) {
            throw ReservationQueueException.unavailable();
        }
        if (!admitted) {
            throw ReservationQueueException.rateLimited();
        }
    }
}
