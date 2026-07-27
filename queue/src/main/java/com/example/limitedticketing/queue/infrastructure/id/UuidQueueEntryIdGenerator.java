package com.example.limitedticketing.queue.infrastructure.id;

import com.example.limitedticketing.queue.application.port.out.QueueEntryIdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidQueueEntryIdGenerator implements QueueEntryIdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
