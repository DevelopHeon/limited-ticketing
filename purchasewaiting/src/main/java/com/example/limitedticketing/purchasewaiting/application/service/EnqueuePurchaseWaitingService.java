package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.MemberEligibilityPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingAdmissionPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingIdGeneratorPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingSequencePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.SaleProductQueryPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.EnqueuePurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.EnqueuePurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingInsertResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EnqueuePurchaseWaitingService implements EnqueuePurchaseWaitingUseCase {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EnqueuePurchaseWaitingService.class);
    private final MemberEligibilityPort memberEligibilityPort;
    private final SaleProductQueryPort saleProductQueryPort;
    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingIdGeneratorPort purchaseWaitingIdGeneratorPort;
    private final PurchaseWaitingSequencePort purchaseWaitingSequencePort;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    private final PurchaseWaitingAdmissionPort purchaseWaitingAdmissionPort;
    private final Clock clock;
    private final int maxOutstanding;

    public EnqueuePurchaseWaitingService(
        MemberEligibilityPort memberEligibilityPort,
        SaleProductQueryPort saleProductQueryPort,
        PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingIdGeneratorPort purchaseWaitingIdGeneratorPort,
        PurchaseWaitingSequencePort purchaseWaitingSequencePort,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort,
        PurchaseWaitingAdmissionPort purchaseWaitingAdmissionPort,
        Clock clock,
        @Value("${limited-ticketing.queue.admission.max-outstanding:10000}") int maxOutstanding
    ) {
        this.memberEligibilityPort = memberEligibilityPort;
        this.saleProductQueryPort = saleProductQueryPort;
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingIdGeneratorPort = purchaseWaitingIdGeneratorPort;
        this.purchaseWaitingSequencePort = purchaseWaitingSequencePort;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
        this.purchaseWaitingAdmissionPort = purchaseWaitingAdmissionPort;
        this.clock = clock;
        if (maxOutstanding < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.maxOutstanding = maxOutstanding;
    }

    @Override
    @Transactional
    public UUID enqueue(EnqueuePurchaseWaitingCommand command) {
        PurchaseWaitingEntry existing = purchaseWaitingEntryRepository.findByMemberIdAndPurchaseRequestKey(
                command.memberId(),
                command.purchaseRequestKey()
            )
            .orElse(null);
        if (existing != null) {
            return resultOf(existing, command.saleProductId());
        }

        LocalDateTime requestedAt = LocalDateTime.now(clock);
        memberEligibilityPort.ensurePurchasable(command.memberId());
        saleProductQueryPort.ensurePurchasable(command.saleProductId(), requestedAt);

        purchaseWaitingEntryRepository.lockQueue(command.saleProductId());
        PurchaseWaitingEntry concurrentRetry = purchaseWaitingEntryRepository.findByMemberIdAndPurchaseRequestKey(
            command.memberId(), command.purchaseRequestKey()).orElse(null);
        if (concurrentRetry != null) {
            return resultOf(concurrentRetry, command.saleProductId());
        }
        purchaseWaitingAdmissionPort.check(command.memberId(), command.saleProductId());
        if (purchaseWaitingEntryRepository.countActive(command.saleProductId()) >= maxOutstanding) {
            throw PurchaseWaitingException.capacityExceeded();
        }

        PurchaseWaitingEntry entry = PurchaseWaitingEntry.enqueue(
            purchaseWaitingIdGeneratorPort.generate(),
            command.memberId(),
            command.saleProductId(),
            command.purchaseRequestKey(),
            purchaseWaitingSequencePort.nextSequence(command.saleProductId()),
            requestedAt
        );
        PurchaseWaitingInsertResult savedEntry = purchaseWaitingEntryRepository.saveIfAbsent(entry);
        if (savedEntry.created()) {
            registerRedisEnqueueAfterCommit(savedEntry.entry());
        }

        return resultOf(savedEntry.entry(), command.saleProductId());
    }

    private void registerRedisEnqueueAfterCommit(PurchaseWaitingEntry entry) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    purchaseWaitingQueuePort.enqueue(entry);
                } catch (RuntimeException exception) {
                    log.error("Reservation queue entry {} was committed but could not be added to Redis", entry.getId(), exception);
                }
            }
        });
    }

    private UUID resultOf(PurchaseWaitingEntry entry, Long saleProductId) {
        if (!entry.getSaleProductId().equals(saleProductId)) {
            throw PurchaseWaitingException.requestKeyReuse();
        }

        return entry.getId();
    }

}
