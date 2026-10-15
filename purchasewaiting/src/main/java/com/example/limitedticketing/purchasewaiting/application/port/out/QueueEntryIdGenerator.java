package com.example.limitedticketing.purchasewaiting.application.port.out;

import java.util.UUID;

public interface QueueEntryIdGenerator {

    UUID generate();
}
