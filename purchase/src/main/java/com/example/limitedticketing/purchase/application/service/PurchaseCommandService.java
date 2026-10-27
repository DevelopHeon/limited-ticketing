package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.port.out.InventoryReservationPort;
import com.example.limitedticketing.purchase.application.port.out.MemberEligibilityPort;
import com.example.limitedticketing.purchase.application.port.out.SaleProductQueryPort;
import com.example.limitedticketing.purchase.application.port.out.dto.PurchasableSaleProduct;
import com.example.limitedticketing.purchase.application.usecase.CancelPurchaseUseCase;
import com.example.limitedticketing.purchase.application.usecase.MarkPurchasePaidUseCase;
import com.example.limitedticketing.purchase.application.usecase.ReservePurchaseUseCase;
import com.example.limitedticketing.purchase.application.usecase.dto.command.CancelPurchaseCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.command.MarkPurchasePaidCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.command.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.usecase.dto.result.PurchaseReservationResult;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.IdempotentPurchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseCommandService implements ReservePurchaseUseCase, MarkPurchasePaidUseCase, CancelPurchaseUseCase {

    private static final int PURCHASE_QUANTITY = 1;
    private static final int RESERVATION_MINUTES = 5;

    private final MemberEligibilityPort memberEligibilityPort;
    private final SaleProductQueryPort saleProductQueryPort;
    private final InventoryReservationPort inventoryReservationPort;
    private final PurchaseRepository purchaseRepository;
    private final Clock clock;

    public PurchaseCommandService(
        MemberEligibilityPort memberEligibilityPort,
        SaleProductQueryPort saleProductQueryPort,
        InventoryReservationPort inventoryReservationPort,
        PurchaseRepository purchaseRepository,
        Clock clock
    ) {
        this.memberEligibilityPort = memberEligibilityPort;
        this.saleProductQueryPort = saleProductQueryPort;
        this.inventoryReservationPort = inventoryReservationPort;
        this.purchaseRepository = purchaseRepository;
        this.clock = clock;
    }

    @Transactional
    public PurchaseReservationResult reserve(ReservePurchaseCommand command) {
        Purchase existing = purchaseRepository.findByMemberIdAndPurchaseRequestKey(
                command.memberId(),
                command.purchaseRequestKey()
            )
            .orElse(null);
        if (existing != null) {
            return reservationResultOf(existing, command.saleProductId());
        }

        LocalDateTime requestedAt = LocalDateTime.now(clock);

        memberEligibilityPort.ensurePurchasable(command.memberId());
        PurchasableSaleProduct saleProduct = saleProductQueryPort.findPurchasable(
            command.saleProductId(), requestedAt
        );
        IdempotentPurchase savedPurchase = purchaseRepository.saveIfAbsent(Purchase.reserve(
            command.memberId(),
            saleProduct.saleProductId(),
            command.purchaseRequestKey(),
            saleProduct.price(),
            requestedAt.plusMinutes(RESERVATION_MINUTES)
        ));
        Purchase purchase = savedPurchase.purchase();
        if (!savedPurchase.created()) {
            return reservationResultOf(purchase, command.saleProductId());
        }

        inventoryReservationPort.reserve(saleProduct.saleProductId(), PURCHASE_QUANTITY);

        return reservationResultOf(purchase, command.saleProductId());
    }

    @Transactional
    public void markPaid(MarkPurchasePaidCommand command) {
        Purchase purchase = purchaseRepository.getRequiredById(command.purchaseId());

        purchase.markPaid();
        purchaseRepository.transitionFromPending(purchase);
    }

    @Transactional
    public void cancel(CancelPurchaseCommand command) {
        Purchase purchase = purchaseRepository.getRequiredById(command.purchaseId());

        purchase.cancelBy(command.memberId());
        purchaseRepository.transitionFromPending(purchase);
        inventoryReservationPort.releaseReservation(
            purchase.getSaleProductId(), purchase.getQuantity()
        );
    }

    private PurchaseReservationResult reservationResultOf(Purchase purchase, Long saleProductId) {
        if (!purchase.getSaleProductId().equals(saleProductId)) {
            throw PurchaseException.requestKeyReuse();
        }

        return new PurchaseReservationResult(purchase.getId(), purchase.getStatus(), purchase.getExpiresAt());
    }
}
