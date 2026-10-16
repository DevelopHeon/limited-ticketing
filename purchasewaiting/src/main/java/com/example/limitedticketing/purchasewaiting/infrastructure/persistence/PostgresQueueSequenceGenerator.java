package com.example.limitedticketing.purchasewaiting.infrastructure.persistence;

import com.example.limitedticketing.purchasewaiting.application.port.out.QueueSequenceGenerator;
import com.example.limitedticketing.purchasewaiting.infrastructure.persistence.repository.JpaReservationQueueEntryRepository;
import org.springframework.stereotype.Component;

@Component
public class PostgresQueueSequenceGenerator implements QueueSequenceGenerator {

    private final JpaReservationQueueEntryRepository repository;

    public PostgresQueueSequenceGenerator(JpaReservationQueueEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public long nextSequence(Long saleProductId) {
        return repository.nextSequence(saleProductId);
    }
}
