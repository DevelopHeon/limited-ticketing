package com.example.limitedticketing.queue.application.port.out;

import java.util.UUID;

public interface QueueEntryIdGenerator {

    UUID generate();
}
