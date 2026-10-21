package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseReservationLookupPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReplayPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReplayPurchaseWaitingService implements ReplayPurchaseWaitingUseCase {
    private static final Logger log = LoggerFactory.getLogger(ReplayPurchaseWaitingService.class);
    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    private final PurchaseReservationLookupPort purchaseReservationLookupPort;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    private final Clock clock;
    private final int maxOutstanding;

    public ReplayPurchaseWaitingService(PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingOutboxPort purchaseWaitingOutboxPort, PurchaseReservationLookupPort purchaseReservationLookupPort,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort, Clock clock,
        @Value("${limited-ticketing.queue.admission.max-outstanding:10000}") int maxOutstanding) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingOutboxPort = purchaseWaitingOutboxPort;
        this.purchaseReservationLookupPort = purchaseReservationLookupPort;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
        this.clock = clock;
        this.maxOutstanding = maxOutstanding;
    }

    @Override
    @Transactional
    public PurchaseWaitingResult replay(UUID waitingEntryId) {
        Long saleProductId = purchaseWaitingEntryRepository.findSaleProductId(waitingEntryId).orElseThrow(PurchaseWaitingException::notFound);
        purchaseWaitingEntryRepository.lockAdmission();
        purchaseWaitingEntryRepository.lockQueue(saleProductId);
        PurchaseWaitingEntry entry = purchaseWaitingEntryRepository.findByIdForUpdate(waitingEntryId).orElseThrow(PurchaseWaitingException::notFound);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean technicalFailure = entry.getStatus() == PurchaseWaitingStatus.FAILED
            && PurchaseWaitingEntry.TECHNICAL_FAILURE_CODE.equals(entry.getFailureCode());
        boolean delayed = entry.getStatus() == PurchaseWaitingStatus.DISPATCHED
            && purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), entry.getProcessingToken())
                .filter(publishedAt -> !publishedAt.plusSeconds(60).isAfter(now)).isPresent();
        if (!technicalFailure && !delayed) {
            throw PurchaseWaitingException.invalidState("Only technical failures and published deliveries delayed for 60 seconds can be replayed");
        }
        UUID previousToken = entry.getProcessingToken();
        Optional<Long> purchaseId = purchaseReservationLookupPort.findPurchaseId(entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey());
        if (purchaseId.isPresent()) {
            entry.restoreReservation(purchaseId.get(), now);
        } else {
            if (technicalFailure && purchaseWaitingEntryRepository.countActive(entry.getSaleProductId()) >= maxOutstanding) {
                throw PurchaseWaitingException.capacityExceeded();
            }
            if (purchaseWaitingEntryRepository.existsOtherActiveEntry(entry.getMemberId(), entry.getSaleProductId(), entry.getId())) {
                throw PurchaseWaitingException.activeEntryExists();
            }
            entry.replay(now);
        }
        PurchaseWaitingEntry saved = purchaseWaitingEntryRepository.save(entry);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (saved.getStatus() == PurchaseWaitingStatus.WAITING) {
                        purchaseWaitingQueuePort.requeue(saved, previousToken);
                    } else {
                        purchaseWaitingQueuePort.releaseProcessing(saved.getSaleProductId(), saved.getId(), previousToken);
                    }
                } catch (RuntimeException exception) {
                    log.warn("Replay committed; Redis synchronization deferred for entry {}", saved.getId(), exception);
                }
            }
        });
        return PurchaseWaitingResult.from(saved, null);
    }
}
