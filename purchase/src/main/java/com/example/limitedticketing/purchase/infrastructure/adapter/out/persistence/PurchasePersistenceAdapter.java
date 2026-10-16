package com.example.limitedticketing.purchase.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.domain.repository.IdempotentPurchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.purchase.infrastructure.adapter.out.persistence.entity.PurchaseJpaEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PurchasePersistenceAdapter implements PurchaseRepository {

    private final PurchaseJpaRepository purchaseJpaRepository;

    public PurchasePersistenceAdapter(PurchaseJpaRepository purchaseJpaRepository) {
        this.purchaseJpaRepository = purchaseJpaRepository;
    }

    @Override
    public Purchase save(Purchase purchase) {
        return purchaseJpaRepository.save(PurchaseJpaEntity.from(purchase)).toDomain();
    }

    @Override
    public void transitionFromPending(Purchase purchase) {
        int updated = purchaseJpaRepository.transitionIfPending(
            purchase.getId(), purchase.getStatus().name(), purchase.activePurchaseKey()
        );
        if (updated == 0) {
            throw PurchaseException.invalidState("Purchase is no longer pending payment");
        }
    }

    @Override
    public IdempotentPurchase saveIfAbsent(Purchase purchase) {
        int inserted = purchaseJpaRepository.insertIfAbsent(
            purchase.getMemberId(),
            purchase.getSaleProductId(),
            purchase.getPurchaseRequestKey(),
            purchase.activePurchaseKey(),
            purchase.getPrice().getAmount(),
            purchase.getQuantity(),
            purchase.getExpiresAt(),
            purchase.getStatus().name()
        );

        Optional<Purchase> persisted = findByMemberIdAndPurchaseRequestKey(
            purchase.getMemberId(),
            purchase.getPurchaseRequestKey()
        );
        if (persisted.isPresent()) {
            return new IdempotentPurchase(persisted.get(), inserted == 1);
        }
        if (inserted == 0) {
            throw PurchaseException.activePurchaseExists();
        }

        throw PurchaseException.persistenceFailure();
    }

    @Override
    public Optional<Purchase> findById(Long purchaseId) {
        return purchaseJpaRepository.findById(purchaseId).map(PurchaseJpaEntity::toDomain);
    }

    @Override
    public Optional<Purchase> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey) {
        return purchaseJpaRepository.findByMemberIdAndPurchaseRequestKey(memberId, purchaseRequestKey)
            .map(PurchaseJpaEntity::toDomain);
    }

    @Override
    public List<Purchase> findPendingPaymentExpiredAtOrBefore(LocalDateTime now) {
        return purchaseJpaRepository.findByStatusAndExpiresAtLessThanEqual(PurchaseStatus.PENDING_PAYMENT, now)
            .stream()
            .map(PurchaseJpaEntity::toDomain)
            .toList();
    }
}
