package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.application.usecase.OutboxTransactionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.PublishReservationOutboxUseCase;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishReservationOutboxService implements PublishReservationOutboxUseCase {
    private static final Logger log = LoggerFactory.getLogger(PublishReservationOutboxService.class);
    private final OutboxTransactionUseCase outboxTransactionUseCase;
    private final ReservationMessagePublisherPort reservationMessagePublisherPort;
    private final Executor outboxCallbackExecutor;
    private final Semaphore inFlight;
    private final int batchSize;

    public PublishReservationOutboxService(OutboxTransactionUseCase outboxTransactionUseCase,
        ReservationMessagePublisherPort reservationMessagePublisherPort,
        @Qualifier("outboxCallbackExecutor") Executor outboxCallbackExecutor,
        @Value("${limited-ticketing.kafka.outbox.max-in-flight:10}") int maxInFlight,
        @Value("${limited-ticketing.kafka.outbox.batch-size:10}") int batchSize) {
        if (maxInFlight <= 0 || batchSize <= 0) {
            throw new IllegalArgumentException("Outbox capacity and batch size must be positive");
        }
        this.outboxTransactionUseCase = outboxTransactionUseCase;
        this.reservationMessagePublisherPort = reservationMessagePublisherPort;
        this.outboxCallbackExecutor = outboxCallbackExecutor;
        this.inFlight = new Semaphore(maxInFlight);
        this.batchSize = batchSize;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void publishReady() {
        int capacity = 0;
        while (capacity < batchSize && inFlight.tryAcquire()) {
            capacity++;
        }
        if (capacity == 0) {
            return;
        }
        List<ReservationOutboxClaim> claims;
        try {
            claims = outboxTransactionUseCase.claimReady(capacity);
        } catch (RuntimeException exception) {
            inFlight.release(capacity);
            throw exception;
        }
        inFlight.release(capacity - claims.size());
        claims.forEach(this::publish);
    }

    private void publish(ReservationOutboxClaim claim) {
        ReservationOutboxMessage message = claim.message();
        try {
            reservationMessagePublisherPort.publish(message.topic(), message.key(), message.payload())
                .whenComplete((ignored, failure) -> submitCompletion(claim, failure));
        } catch (RuntimeException exception) {
            submitCompletion(claim, exception);
        }
    }

    private void submitCompletion(ReservationOutboxClaim claim, Throwable failure) {
        try {
            outboxCallbackExecutor.execute(() -> recordCompletion(claim, failure));
        } catch (RejectedExecutionException exception) {
            inFlight.release();
            log.warn("Outbox callback rejected for event {}; claim will expire", claim.message().eventId(), exception);
        }
    }

    private void recordCompletion(ReservationOutboxClaim claim, Throwable failure) {
        try {
            boolean recorded = failure == null ? outboxTransactionUseCase.complete(claim)
                : outboxTransactionUseCase.recordFailure(claim, failure);
            if (!recorded) {
                log.warn("Outbox callback lost claim ownership for event {}", claim.message().eventId());
            }
        } catch (RuntimeException exception) {
            log.warn("Outbox completion failed for event {}; claim will expire", claim.message().eventId(), exception);
        } finally {
            inFlight.release();
        }
    }
}
