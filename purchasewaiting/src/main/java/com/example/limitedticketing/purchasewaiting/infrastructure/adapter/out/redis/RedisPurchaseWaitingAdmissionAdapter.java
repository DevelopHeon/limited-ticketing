package com.example.limitedticketing.purchasewaiting.infrastructure.adapter.out.redis;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingAdmissionPort;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPurchaseWaitingAdmissionAdapter implements PurchaseWaitingAdmissionPort {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisPurchaseWaitingAdmissionAdapter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void check(Long memberId, Long saleProductId) {
        boolean admitted;
        try {
            admitted = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                "admission:sale:" + saleProductId + ":member:" + memberId, "1", Duration.ofSeconds(1)));
        } catch (DataAccessException exception) {
            throw PurchaseWaitingException.unavailable();
        }
        if (!admitted) {
            throw PurchaseWaitingException.rateLimited();
        }
    }
}
