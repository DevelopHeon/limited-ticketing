package com.example.limitedticketing.purchase.application.command.service;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandFacade;
import com.example.limitedticketing.inventory.application.port.in.dto.ReleaseInventoryReservationInput;
import com.example.limitedticketing.inventory.application.port.in.dto.ReserveInventoryInput;
import com.example.limitedticketing.member.application.port.in.MemberPurchaseEligibilityFacade;
import com.example.limitedticketing.member.application.port.in.dto.EnsureMemberPurchasableInput;
import com.example.limitedticketing.purchase.application.command.dto.PurchaseReservationResult;
import com.example.limitedticketing.purchase.application.command.dto.CancelPurchaseCommand;
import com.example.limitedticketing.purchase.application.command.dto.MarkPurchasePaidCommand;
import com.example.limitedticketing.purchase.application.command.dto.ReservePurchaseCommand;
import com.example.limitedticketing.purchase.application.port.out.PurchaseEventPublisher;
import com.example.limitedticketing.purchase.application.support.PurchaseFinder;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.IdempotentPurchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.sale.application.port.in.SaleProductPurchaseFacade;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.in.dto.PurchasableSaleProduct;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseCommandService {

    private static final int PURCHASE_QUANTITY = 1;
    private static final int RESERVATION_MINUTES = 5;

    private final MemberPurchaseEligibilityFacade memberPurchaseEligibilityFacade;
    private final SaleProductPurchaseFacade saleProductPurchaseFacade;
    private final InventoryCommandFacade inventoryCommandFacade;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseFinder purchaseFinder;
    private final Clock clock;
    private final PurchaseEventPublisher purchaseEventPublisher;

    public PurchaseCommandService(
        MemberPurchaseEligibilityFacade memberPurchaseEligibilityFacade,
        SaleProductPurchaseFacade saleProductPurchaseFacade,
        InventoryCommandFacade inventoryCommandFacade,
        PurchaseRepository purchaseRepository,
        PurchaseFinder purchaseFinder,
        Clock clock,
        PurchaseEventPublisher purchaseEventPublisher
    ) {
        this.memberPurchaseEligibilityFacade = memberPurchaseEligibilityFacade;
        this.saleProductPurchaseFacade = saleProductPurchaseFacade;
        this.inventoryCommandFacade = inventoryCommandFacade;
        this.purchaseRepository = purchaseRepository;
        this.purchaseFinder = purchaseFinder;
        this.clock = clock;
        this.purchaseEventPublisher = purchaseEventPublisher;
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

        memberPurchaseEligibilityFacade.ensurePurchasable(new EnsureMemberPurchasableInput(command.memberId()));
        PurchasableSaleProduct saleProduct = saleProductPurchaseFacade.findPurchasable(
            new FindPurchasableSaleProductInput(command.saleProductId(), requestedAt)
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

        inventoryCommandFacade.reserve(new ReserveInventoryInput(saleProduct.saleProductId(), PURCHASE_QUANTITY));
        purchaseEventPublisher.publish(purchase.reservationEvent());

        return reservationResultOf(purchase, command.saleProductId());
    }

    @Transactional
    public void markPaid(MarkPurchasePaidCommand command) {
        Purchase purchase = purchaseFinder.getRequired(command.purchaseId());

        purchase.markPaid();
        purchaseRepository.transitionFromPending(purchase);
    }

    @Transactional
    public void cancel(CancelPurchaseCommand command) {
        Purchase purchase = purchaseFinder.getRequired(command.purchaseId());

        purchase.cancelBy(command.memberId());
        purchaseRepository.transitionFromPending(purchase);
        inventoryCommandFacade.releaseReservation(
            new ReleaseInventoryReservationInput(purchase.getSaleProductId(), purchase.getQuantity())
        );
    }

    private PurchaseReservationResult reservationResultOf(Purchase purchase, Long saleProductId) {
        if (!purchase.getSaleProductId().equals(saleProductId)) {
            throw PurchaseException.requestKeyReuse();
        }

        return new PurchaseReservationResult(purchase.getId(), purchase.getStatus(), purchase.getExpiresAt());
    }
}
