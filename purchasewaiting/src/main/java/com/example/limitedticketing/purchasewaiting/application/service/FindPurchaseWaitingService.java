package com.example.limitedticketing.purchasewaiting.application.service;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.FindPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.query.FindPurchaseWaitingQuery;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.PurchaseWaitingResult;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindPurchaseWaitingService implements FindPurchaseWaitingUseCase {

    private final PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    private final PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    public FindPurchaseWaitingService(
        PurchaseWaitingEntryRepository purchaseWaitingEntryRepository,
        PurchaseWaitingQueuePort purchaseWaitingQueuePort
    ) {
        this.purchaseWaitingEntryRepository = purchaseWaitingEntryRepository;
        this.purchaseWaitingQueuePort = purchaseWaitingQueuePort;
    }

    private OptionalLong findPosition(Long saleProductId, UUID entryId) {
        try {
            return purchaseWaitingQueuePort.findPosition(saleProductId, entryId);
        } catch (PurchaseWaitingException exception) {
            if (!"QUEUE_UNAVAILABLE".equals(exception.getCode())) {
                throw exception;
            }
            return OptionalLong.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseWaitingResult find(FindPurchaseWaitingQuery query) {
        PurchaseWaitingEntry entry = purchaseWaitingEntryRepository.getRequiredById(query.waitingEntryId());
        if (!entry.getMemberId().equals(query.memberId())) {
            throw PurchaseWaitingException.ownershipViolation();
        }

        OptionalLong position = entry.getStatus() == PurchaseWaitingStatus.WAITING
            ? findPosition(entry.getSaleProductId(), entry.getId())
            : OptionalLong.empty();

        return PurchaseWaitingResult.from(
            entry,
            position.isPresent() ? position.getAsLong() : null
        );
    }

}
