package com.example.limitedticketing.purchase.application.command.service;

import com.example.limitedticketing.inventory.application.port.in.InventoryCommandFacade;
import com.example.limitedticketing.inventory.application.port.in.dto.ReleaseInventoryReservationInput;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseExpirationService {

    private final PurchaseRepository purchaseRepository;
    private final InventoryCommandFacade inventoryCommandFacade;
    private final Clock clock;

    public PurchaseExpirationService(
        PurchaseRepository purchaseRepository,
        InventoryCommandFacade inventoryCommandFacade,
        Clock clock
    ) {
        this.purchaseRepository = purchaseRepository;
        this.inventoryCommandFacade = inventoryCommandFacade;
        this.clock = clock;
    }

    @Transactional
    public int expireDuePurchases() {
        LocalDateTime now = LocalDateTime.now(clock);

        return purchaseRepository.findPendingPaymentExpiredAtOrBefore(now)
            .stream()
            .mapToInt(purchase -> expire(purchase, now))
            .sum();
    }

    private int expire(Purchase purchase, LocalDateTime now) {
        purchase.expire(now);
        inventoryCommandFacade.releaseReservation(
            new ReleaseInventoryReservationInput(purchase.getSaleProductId(), purchase.getQuantity())
        );
        purchaseRepository.save(purchase);

        return 1;
    }
}
