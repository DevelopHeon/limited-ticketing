package com.example.limitedticketing.purchasewaiting.application.command.service;

import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityFacade;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.purchasewaiting.application.command.dto.EnqueueReservationCommand;
import com.example.limitedticketing.purchasewaiting.application.command.dto.ReservationQueueEntryResult;
import com.example.limitedticketing.purchasewaiting.application.port.out.QueueEntryIdGenerator;
import com.example.limitedticketing.purchasewaiting.application.port.out.QueueSequenceGenerator;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueStore;
import com.example.limitedticketing.purchasewaiting.domain.exception.ReservationQueueException;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.IdempotentReservationQueueEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.ReservationQueueEntryRepository;
import com.example.limitedticketing.sale.application.port.in.SaleProductPurchaseFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationQueueAdmissionGuard;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationQueueCommandService {

    private static final Logger log = LoggerFactory.getLogger(ReservationQueueCommandService.class);

    private final MemberPurchaseEligibilityFacade memberPurchaseEligibilityFacade;
    private final SaleProductPurchaseFacade saleProductPurchaseFacade;
    private final ReservationQueueEntryRepository reservationQueueEntryRepository;
    private final QueueEntryIdGenerator queueEntryIdGenerator;
    private final QueueSequenceGenerator queueSequenceGenerator;
    private final ReservationQueueStore reservationQueueStore;
    private final Clock clock;
    private final ReservationQueueAdmissionGuard admissionGuard;
    private final int maxOutstanding;

    public ReservationQueueCommandService(
        MemberPurchaseEligibilityFacade memberPurchaseEligibilityFacade,
        SaleProductPurchaseFacade saleProductPurchaseFacade,
        ReservationQueueEntryRepository reservationQueueEntryRepository,
        QueueEntryIdGenerator queueEntryIdGenerator,
        QueueSequenceGenerator queueSequenceGenerator,
        ReservationQueueStore reservationQueueStore,
        Clock clock,
        ReservationQueueAdmissionGuard admissionGuard,
        @Value("${limited-ticketing.queue.admission.max-outstanding:10000}") int maxOutstanding
    ) {
        this.memberPurchaseEligibilityFacade = memberPurchaseEligibilityFacade;
        this.saleProductPurchaseFacade = saleProductPurchaseFacade;
        this.reservationQueueEntryRepository = reservationQueueEntryRepository;
        this.queueEntryIdGenerator = queueEntryIdGenerator;
        this.queueSequenceGenerator = queueSequenceGenerator;
        this.reservationQueueStore = reservationQueueStore;
        this.clock = clock;
        this.admissionGuard = admissionGuard;
        if (maxOutstanding < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.maxOutstanding = maxOutstanding;
    }

    @Transactional
    public ReservationQueueEntryResult enqueue(EnqueueReservationCommand command) {
        ReservationQueueEntry existing = reservationQueueEntryRepository.findByMemberIdAndPurchaseRequestKey(
                command.memberId(),
                command.purchaseRequestKey()
            )
            .orElse(null);
        if (existing != null) {
            return resultOf(existing, command.saleProductId());
        }

        LocalDateTime requestedAt = LocalDateTime.now(clock);
        memberPurchaseEligibilityFacade.ensurePurchasable(new EnsureMemberPurchasableInput(command.memberId()));
        saleProductPurchaseFacade.findPurchasable(new FindPurchasableSaleProductInput(command.saleProductId(), requestedAt));

        reservationQueueEntryRepository.lockQueue(command.saleProductId());
        ReservationQueueEntry concurrentRetry = reservationQueueEntryRepository.findByMemberIdAndPurchaseRequestKey(
            command.memberId(), command.purchaseRequestKey()).orElse(null);
        if (concurrentRetry != null) {
            return resultOf(concurrentRetry, command.saleProductId());
        }
        admissionGuard.check(command.memberId(), command.saleProductId());
        if (reservationQueueEntryRepository.countActive(command.saleProductId()) >= maxOutstanding) {
            throw ReservationQueueException.capacityExceeded();
        }

        ReservationQueueEntry entry = ReservationQueueEntry.enqueue(
            queueEntryIdGenerator.generate(),
            command.memberId(),
            command.saleProductId(),
            command.purchaseRequestKey(),
            queueSequenceGenerator.nextSequence(command.saleProductId()),
            requestedAt
        );
        IdempotentReservationQueueEntry savedEntry = reservationQueueEntryRepository.saveIfAbsent(entry);
        if (savedEntry.created()) {
            registerRedisEnqueueAfterCommit(savedEntry.entry());
        }

        return resultOf(savedEntry.entry(), command.saleProductId());
    }

    private void registerRedisEnqueueAfterCommit(ReservationQueueEntry entry) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    reservationQueueStore.enqueue(entry);
                } catch (RuntimeException exception) {
                    log.error("Reservation queue entry {} was committed but could not be added to Redis", entry.getId(), exception);
                }
            }
        });
    }

    private ReservationQueueEntryResult resultOf(ReservationQueueEntry entry, Long saleProductId) {
        if (!entry.getSaleProductId().equals(saleProductId)) {
            throw ReservationQueueException.requestKeyReuse();
        }

        return ReservationQueueEntryResult.from(entry);
    }
}
