package com.example.limitedticketing.purchasewaiting.infrastructure.redis;

import org.springframework.stereotype.Component;

@Component
public class ReservationQueueRedisKeys {

    private static final String WAITING_PREFIX = "waiting:sale:";
    private static final String PROCESSING_PREFIX = "processing:sale:";
    private static final String SEQUENCE_PREFIX = "sequence:sale:";

    public String waiting(Long saleProductId) {
        return WAITING_PREFIX + saleProductId;
    }

    public String processing(Long saleProductId) {
        return PROCESSING_PREFIX + saleProductId;
    }

    public String tokens(Long saleProductId) {
        return "processing-tokens:sale:" + saleProductId;
    }

    public String tokensPattern() {
        return "processing-tokens:sale:*";
    }

    public String sequence(Long saleProductId) {
        return SEQUENCE_PREFIX + saleProductId;
    }

    public String waitingPattern() {
        return WAITING_PREFIX + "*";
    }

    public String processingPattern() {
        return PROCESSING_PREFIX + "*";
    }

    public String sequencePattern() {
        return SEQUENCE_PREFIX + "*";
    }

    public Long saleProductIdOf(String key) {
        return Long.parseLong(key.substring(key.lastIndexOf(':') + 1));
    }
}
