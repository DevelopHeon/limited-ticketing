package com.example.limitedticketing.purchasewaiting.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.query.FindPurchaseWaitingQuery;
import com.example.limitedticketing.purchasewaiting.domain.exception.PurchaseWaitingException;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("구매 대기 조회의 원장 존재 조건")
class FindPurchaseWaitingServiceTest {
    @Mock private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Mock private PurchaseWaitingQueuePort purchaseWaitingQueuePort;

    @Test
    @DisplayName("없는 대기 요청은 Redis 순위를 조회하지 않고 대기 요청 없음으로 응답한다")
    void missingEntryDoesNotReadRedisPosition() {
        UUID waitingEntryId = UUID.randomUUID();
        when(purchaseWaitingEntryRepository.getRequiredById(waitingEntryId)).thenCallRealMethod();

        assertThatThrownBy(() -> new FindPurchaseWaitingService(purchaseWaitingEntryRepository, purchaseWaitingQueuePort)
            .find(new FindPurchaseWaitingQuery(waitingEntryId, 1L)))
            .isInstanceOf(PurchaseWaitingException.class).extracting("code").isEqualTo("QUEUE_ENTRY_NOT_FOUND");

        verifyNoInteractions(purchaseWaitingQueuePort);
    }
}
