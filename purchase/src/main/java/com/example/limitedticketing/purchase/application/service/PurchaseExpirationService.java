package com.example.limitedticketing.purchase.application.service;

import com.example.limitedticketing.purchase.application.port.out.InventoryReservationPort;
import com.example.limitedticketing.purchase.application.usecase.ExpirePurchasesUseCase;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseExpirationService implements ExpirePurchasesUseCase {

    private final PurchaseRepository purchaseRepository;
    private final InventoryReservationPort inventoryReservationPort;
    private final Clock clock;

    public PurchaseExpirationService(
        PurchaseRepository purchaseRepository,
        InventoryReservationPort inventoryReservationPort,
        Clock clock
    ) {
        this.purchaseRepository = purchaseRepository;
        this.inventoryReservationPort = inventoryReservationPort;
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
        purchaseRepository.transitionFromPending(purchase);
        inventoryReservationPort.releaseReservation(
            purchase.getSaleProductId(), purchase.getQuantity()
        );

        return 1;
    }
}
