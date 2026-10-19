package com.example.limitedticketing.purchase.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseByRequestQuery;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.shared.vo.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("실행 결과 복원용 구매 조회의 상품 일치 검증")
class PurchaseRequestQueryServiceTest {
    @Mock private PurchaseRepository purchaseRepository;

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(longs = {2L, 3L})
    @DisplayName("회원과 요청 키가 같아도 판매 상품이 다른 구매는 복원 결과로 반환하지 않는다")
    void onlyTheMatchingSalePurchaseCanBeRestored(Long saleProductId) {
        Purchase purchase = Purchase.reconstitute(91L, 1L, 2L, "key", Money.of(BigDecimal.TEN), 1,
            LocalDateTime.of(2026, 10, 19, 12, 0), PurchaseStatus.PENDING_PAYMENT);
        when(purchaseRepository.findByMemberIdAndPurchaseRequestKey(1L, "key")).thenReturn(Optional.of(purchase));
        Optional<Long> result = new PurchaseQueryService(purchaseRepository)
            .findPurchaseId(new FindPurchaseByRequestQuery(1L, saleProductId, "key"));
        assertThat(result).isEqualTo(saleProductId.equals(2L) ? Optional.of(91L) : Optional.empty());
    }
}
