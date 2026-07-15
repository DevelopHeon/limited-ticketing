package com.example.limitedticketing.purchase.infrastructure.persistence;

import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.purchase.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.example.limitedticketing.purchase.infrastructure.persistence.repository.JpaPurchaseRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseRepositoryAdapter implements PurchaseRepository {

    private final JpaPurchaseRepository jpaPurchaseRepository;

    public PurchaseRepositoryAdapter(JpaPurchaseRepository jpaPurchaseRepository) {
        this.jpaPurchaseRepository = jpaPurchaseRepository;
    }

    @Override
    public Purchase save(Purchase purchase) {
        return jpaPurchaseRepository.save(PurchaseJpaEntity.from(purchase)).toDomain();
    }

    @Override
    public Optional<Purchase> findById(Long purchaseId) {
        return jpaPurchaseRepository.findById(purchaseId).map(PurchaseJpaEntity::toDomain);
    }
}
