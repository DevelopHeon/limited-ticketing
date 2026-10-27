package com.example.limitedticketing.purchase.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.purchase.application.usecase.dto.query.FindPurchaseForPaymentQuery;
import com.example.limitedticketing.purchase.domain.exception.PurchaseException;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 대상 구매 조회의 존재 조건")
class PurchaseQueryServiceTest {
    @Mock private PurchaseRepository purchaseRepository;

    @Test
    @DisplayName("없는 구매를 결제 대상으로 반환하지 않고 구매 없음으로 응답한다")
    void missingPurchaseCannotBecomeAPaymentTarget() {
        when(purchaseRepository.getRequiredById(1L)).thenCallRealMethod();

        assertThatThrownBy(() -> new PurchaseQueryService(purchaseRepository)
            .findForPayment(new FindPurchaseForPaymentQuery(1L)))
            .isInstanceOf(PurchaseException.class).extracting("code").isEqualTo("PURCHASE_NOT_FOUND");
    }
}
