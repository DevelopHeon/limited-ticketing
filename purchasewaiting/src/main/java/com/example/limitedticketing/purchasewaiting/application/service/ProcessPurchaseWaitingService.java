package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryCommandPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingEntryQueryPort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReserveWaitingPurchaseUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReserveWaitingPurchaseCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.shared.exception.DomainException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessPurchaseWaitingService implements ProcessPurchaseWaitingUseCase {

    private final PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort;
    private final PurchaseWaitingEntryCommandPort purchaseWaitingEntryCommandPort;
    private final ReserveWaitingPurchaseUseCase reserveWaitingPurchaseUseCase;
    private final Clock clock;

    public ProcessPurchaseWaitingService(
        PurchaseWaitingEntryQueryPort purchaseWaitingEntryQueryPort,
        PurchaseWaitingEntryCommandPort purchaseWaitingEntryCommandPort,
        ReserveWaitingPurchaseUseCase reserveWaitingPurchaseUseCase,
        Clock clock
    ) {
        this.purchaseWaitingEntryQueryPort = purchaseWaitingEntryQueryPort;
        this.purchaseWaitingEntryCommandPort = purchaseWaitingEntryCommandPort;
        this.reserveWaitingPurchaseUseCase = reserveWaitingPurchaseUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<PurchaseWaitingEntry> process(ProcessPurchaseWaitingCommand command) {
        return purchaseWaitingEntryQueryPort.findByIdForUpdate(command.waitingEntryId())
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
        return purchaseWaitingEntryCommandPort.save(entry);
    }
}
