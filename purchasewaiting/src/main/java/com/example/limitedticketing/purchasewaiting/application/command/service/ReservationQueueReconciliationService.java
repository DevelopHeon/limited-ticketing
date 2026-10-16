package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationQueueReconciliationService {

    private final ReservationQueueEntryRepository repository;
    private final ReservationQueueStore store;

    public ReservationQueueReconciliationService(ReservationQueueEntryRepository repository, ReservationQueueStore store) {
        this.repository = repository;
        this.store = store;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreWaiting(UUID id) {
        repository.findByIdForUpdateSkipLocked(id)
            .filter(entry -> entry.getStatus() == ReservationQueueStatus.WAITING)
            .ifPresent(store::restoreWaiting);
    }
}
