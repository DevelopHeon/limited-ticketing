package com.example.limitedticketing.purchase.infrastructure.adapter.out.persistence;

import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.infrastructure.adapter.out.persistence.entity.PurchaseJpaEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseJpaRepository extends JpaRepository<PurchaseJpaEntity, Long> {

    List<PurchaseJpaEntity> findByStatusAndExpiresAtLessThanEqual(PurchaseStatus status, LocalDateTime expiresAt);

    Optional<PurchaseJpaEntity> findByMemberIdAndPurchaseRequestKey(Long memberId, String purchaseRequestKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        update purchases
        set status = :status,
            active_purchase_key = :activePurchaseKey
        where id = :purchaseId
          and status = 'PENDING_PAYMENT'
        """, nativeQuery = true)
    int transitionIfPending(
        @Param("purchaseId") Long purchaseId,
        @Param("status") String status,
        @Param("activePurchaseKey") String activePurchaseKey
    );

    @Modifying
    @Query(value = """
        insert into purchases (
            member_id,
            sale_product_id,
            purchase_request_key,
            active_purchase_key,
            purchase_price,
            quantity,
            expires_at,
            status
        ) values (
            :memberId,
            :saleProductId,
            :purchaseRequestKey,
            :activePurchaseKey,
            :purchasePrice,
            :quantity,
            :expiresAt,
            :status
        ) on conflict do nothing
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("memberId") Long memberId,
        @Param("saleProductId") Long saleProductId,
        @Param("purchaseRequestKey") String purchaseRequestKey,
        @Param("activePurchaseKey") String activePurchaseKey,
        @Param("purchasePrice") BigDecimal purchasePrice,
        @Param("quantity") int quantity,
        @Param("expiresAt") LocalDateTime expiresAt,
        @Param("status") String status
    );
}
