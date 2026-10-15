package com.example.limitedticketing.purchasewaiting.infrastructure.id;

import com.example.limitedticketing.purchasewaiting.application.port.out.QueueEntryIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidQueueEntryIdGenerator implements QueueEntryIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
