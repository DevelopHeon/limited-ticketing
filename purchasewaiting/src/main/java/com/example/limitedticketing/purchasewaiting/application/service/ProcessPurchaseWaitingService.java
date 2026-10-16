package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReserveWaitingPurchaseUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReserveWaitingPurchaseCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import com.example.limitedticketing.shared.exception.DomainException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessPurchaseWaitingService implements ProcessPurchaseWaitingUseCase {

    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final ReserveWaitingPurchaseUseCase reserveWaitingPurchaseUseCase;
    private final Clock clock;

    public ProcessPurchaseWaitingService(
        PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        ReserveWaitingPurchaseUseCase reserveWaitingPurchaseUseCase,
        Clock clock
    ) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.reserveWaitingPurchaseUseCase = reserveWaitingPurchaseUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<PurchaseWaitingEntry> process(ProcessPurchaseWaitingCommand command) {
        return purchaseWaitingEntryRepository.findByIdForUpdate(command.waitingEntryId())
            .filter(entry -> entry.ownsAttempt(command.token()) && entry.getLeaseUntil().isAfter(LocalDateTime.now(clock)))
            .map(this::reserve);
    }

    private PurchaseWaitingEntry reserve(PurchaseWaitingEntry entry) {
        try {
            Long purchaseId = reserveWaitingPurchaseUseCase.reserve(new ReserveWaitingPurchaseCommand(
                entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey()));
            entry.completeReservation(purchaseId, LocalDateTime.now(clock));
        } catch (DomainException exception) {
            if ("INSUFFICIENT_INVENTORY".equals(exception.getCode())) {
                entry.markSoldOut(exception.getCode(), LocalDateTime.now(clock));
            } else {
                entry.fail(exception.getCode(), LocalDateTime.now(clock));
            }
        }
        return purchaseWaitingEntryRepository.save(entry);
    }
}
