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
import com.example.limitedticketing.purchase.domain.model.Purchase;
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
        LocalDateTime requestedAt = LocalDateTime.now(clock);

        memberPurchaseEligibilityFacade.ensurePurchasable(new EnsureMemberPurchasableInput(command.memberId()));
        PurchasableSaleProduct saleProduct = saleProductPurchaseFacade.findPurchasable(
            new FindPurchasableSaleProductInput(command.saleProductId(), requestedAt)
        );
        inventoryCommandFacade.reserve(new ReserveInventoryInput(saleProduct.saleProductId(), PURCHASE_QUANTITY));

        Purchase purchase = purchaseRepository.save(Purchase.reserve(
            command.memberId(),
            saleProduct.saleProductId(),
            saleProduct.price(),
            requestedAt.plusMinutes(RESERVATION_MINUTES)
        ));
        purchaseEventPublisher.publish(purchase.reservationEvent());

        return new PurchaseReservationResult(purchase.getId(), purchase.getStatus(), purchase.getExpiresAt());
    }

    @Transactional
    public void markPaid(MarkPurchasePaidCommand command) {
        Purchase purchase = purchaseFinder.getRequired(command.purchaseId());

        purchase.markPaid();
        purchaseRepository.save(purchase);
    }

    @Transactional
    public void cancel(CancelPurchaseCommand command) {
        Purchase purchase = purchaseFinder.getRequired(command.purchaseId());

        purchase.cancelBy(command.memberId());
        inventoryCommandFacade.releaseReservation(
            new ReleaseInventoryReservationInput(purchase.getSaleProductId(), purchase.getQuantity())
        );
        purchaseRepository.save(purchase);
    }
}
