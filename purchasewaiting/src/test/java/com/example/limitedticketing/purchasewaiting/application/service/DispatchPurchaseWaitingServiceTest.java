package com.example.limitedticketing.purchasewaiting.application.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Kafka 전달 대기를 포함한 전역 입장 제한")
class DispatchPurchaseWaitingServiceTest {
    @Mock private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Mock private PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    @Mock private ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase;

    @Test
    @DisplayName("DB의 전역 입장 슬롯이 가득 차면 Redis와 추가 입장을 시도하지 않는다")
    void inFlightDeliveriesOccupyGlobalAdmissionSlots() {
        when(purchaseWaitingEntryRepository.countInFlight()).thenReturn(10L);
        DispatchPurchaseWaitingService service = new DispatchPurchaseWaitingService(purchaseWaitingEntryRepository,
            purchaseWaitingQueuePort, claimPurchaseWaitingUseCase, Clock.systemUTC(), 10, 10, 10);
        service.dispatchWaitingEntries();
        verifyNoInteractions(purchaseWaitingQueuePort, claimPurchaseWaitingUseCase);
    }
}
