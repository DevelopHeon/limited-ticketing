package com.example.limitedticketing.integration.purchasewaiting;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyString;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingReservationPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobInput;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.ReservationMessagePublisherPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxClaim;
import com.example.limitedticketing.purchasewaiting.application.usecase.OutboxTransactionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecoverPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.PublishReservationOutboxUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.StartPurchaseWaitingExecutionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecordPurchaseWaitingFailureUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.result.ReservationExecutionResult;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging.ReservationKafkaConsumer;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("입장 상태와 예약 Outbox의 영속 원자성")
class PurchaseWaitingOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleProductRepository saleProductRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Autowired private ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private RecoverPurchaseWaitingUseCase recoverPurchaseWaitingUseCase;
    @Autowired private PurchaseWaitingReservationPort purchaseWaitingReservationPort;
    @Autowired private StartPurchaseWaitingExecutionUseCase startPurchaseWaitingExecutionUseCase;
    @Autowired private RecordPurchaseWaitingFailureUseCase recordPurchaseWaitingFailureUseCase;
    @Autowired private ReservationKafkaConsumer reservationKafkaConsumer;
    @MockitoSpyBean private ReservationMessagePublisherPort reservationMessagePublisherPort;
    @Autowired private PublishReservationOutboxUseCase publishReservationOutboxUseCase;
    @Autowired private OutboxTransactionUseCase outboxTransactionUseCase;

    @Test
    @DisplayName("Kafka 전송은 Claim 커밋 이후 DB 트랜잭션 밖에서 실행한다")
    void kafkaSendDoesNotHoldTheClaimTransaction() {
        PurchaseWaitingEntry entry = createWaiting();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbcTemplate.queryForObject("SELECT claim_id FROM reservation_job_outbox WHERE waiting_entry_id = ?", UUID.class, entry.getId())).isNotNull();
            return CompletableFuture.completedFuture(null);
        }).when(reservationMessagePublisherPort).publish(anyString(), anyString(), anyString());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return invocation.callRealMethod();
        }).when(purchaseWaitingOutboxPort).markPublished(any(), any(), any());

        publishReservationOutboxUseCase.publishReady();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(jdbcTemplate.queryForObject("SELECT published_at FROM reservation_job_outbox WHERE waiting_entry_id = ?", LocalDateTime.class, entry.getId())).isNotNull());
    }

    @Test
    @DisplayName("DB transaction이 열린 진입은 Claim과 Kafka 전송 전에 거부한다")
    void ambientTransactionIsRejectedBeforeClaimingOrPublishing() {
        doAnswer(invocation -> CompletableFuture.completedFuture(null))
            .when(reservationMessagePublisherPort).publish(anyString(), anyString(), anyString());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PurchaseWaitingEntry entry = createWaiting();
            claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
            assertThatThrownBy(publishReservationOutboxUseCase::publishReady).isInstanceOf(IllegalTransactionStateException.class);
            verify(reservationMessagePublisherPort, never()).publish(anyString(), anyString(), anyString());
            assertThat(jdbcTemplate.queryForObject("SELECT claim_id FROM reservation_job_outbox WHERE waiting_entry_id = ?", UUID.class, entry.getId())).isNull();
            assertThat(jdbcTemplate.queryForObject("SELECT publish_attempts FROM reservation_job_outbox WHERE waiting_entry_id = ?", Integer.class, entry.getId())).isZero();
            status.setRollbackOnly();
        });
    }

    @Test
    @DisplayName("실행 실패 기록을 기다리는 동일 메시지는 ACK하지 않고 다시 전달해 예약을 완료한다")
    void activeDuplicateRetainsDeliveryUntilTheFailedAttemptCanRetry() {
        PurchaseWaitingEntry waiting = createWaiting();
        UUID token = UUID.randomUUID();
        PurchaseWaitingEntry entry = claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(waiting.getId(), token)).orElseThrow();
        ReservationOutboxMessage message = readyMessage(entry.getId(), LocalDateTime.now().plusSeconds(1));
        ReservationExecutionCommand command = new ReservationExecutionCommand(message.eventId(), entry.getId(), entry.getSaleProductId(), token);
        startPurchaseWaitingExecutionUseCase.start(command);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(message.topic(), 0, 0, message.key(), message.payload());
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        reservationKafkaConsumer.receive(record, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
        verify(acknowledgment).nack(Duration.ofSeconds(1));
        assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getExecutionAttempts()).isEqualTo(1);
        assertThat(recordPurchaseWaitingFailureUseCase.record(command)).isEqualTo(ReservationExecutionResult.RETRY);
        reservationKafkaConsumer.receive(record, acknowledgment);
        verify(acknowledgment).acknowledge();
        assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(jdbcTemplate.queryForObject("SELECT reserved_quantity FROM inventories WHERE sale_product_id = ?", Long.class, entry.getSaleProductId())).isEqualTo(1L);
    }


    @Test
    @DisplayName("Claim 커밋 후에는 Lease까지 재선택을 막고 만료 후 이전 콜백의 상태 변경을 거부한다")
    void expiredClaimCanBeRecoveredAndStaleCallbacksAreFenced() {
        PurchaseWaitingEntry entry = createWaiting();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        ReservationOutboxMessage original = readyMessage(entry.getId(), now);
        ReservationOutboxClaim first = claimMessage(original.eventId(), now);
        assertThat(claimMessages(now.plusSeconds(29))).extracting(claim -> claim.message().eventId()).doesNotContain(original.eventId());
        assertThat(purchaseWaitingOutboxPort.markPublished(original.eventId(), first.claimId(), now.plusSeconds(30))).isFalse();
        ReservationOutboxClaim recovered = claimMessage(original.eventId(), now.plusSeconds(30));
        assertThat(recovered.claimId()).isNotEqualTo(first.claimId());
        assertThat(recovered.message().payload()).isEqualTo(original.payload());
        assertThat(recovered.message().attempts()).isEqualTo(2);
        assertThat(purchaseWaitingOutboxPort.markPublished(original.eventId(), first.claimId(), now.plusSeconds(31))).isFalse();
        assertThat(purchaseWaitingOutboxPort.recordFailure(original.eventId(), first.claimId(), now.plusSeconds(31), now.plusSeconds(32), "OLD_OWNER")).isFalse();
        assertThat(purchaseWaitingOutboxPort.markPublished(original.eventId(), recovered.claimId(), now.plusSeconds(31))).isTrue();
        assertThat(purchaseWaitingOutboxPort.recordFailure(original.eventId(), recovered.claimId(), now.plusSeconds(32), now.plusSeconds(33), "LATE_FAILURE")).isFalse();
        assertThat(claimMessages(now.plusSeconds(90))).extracting(claim -> claim.message().eventId()).doesNotContain(original.eventId());
    }

    @Test
    @DisplayName("동시에 Claim하는 Publisher 중 한 곳만 메시지를 소유한다")
    void concurrentPublishersCannotClaimTheSameMessage() throws Exception {
        PurchaseWaitingEntry entry = createWaiting();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        UUID eventId = readyMessage(entry.getId(), now).eventId();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<ReservationOutboxClaim>>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executorService.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Claim start timed out");
                    }
                    return claimMessages(now);
                }));
            }
            start.countDown();
            List<ReservationOutboxClaim> selected = new ArrayList<>();
            for (Future<List<ReservationOutboxClaim>> result : results) {
                selected.addAll(result.get(5, TimeUnit.SECONDS));
            }
            assertThat(selected.stream().filter(claim -> claim.message().eventId().equals(eventId))).hasSize(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Claim은 호출자 transaction이 롤백되어도 별도로 커밋되며 완료는 새 transaction에서 기록한다")
    void claimAndCompletionHaveIndependentTransactions() {
        PurchaseWaitingEntry entry = createWaiting();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
        UUID eventId = readyMessage(entry.getId(), LocalDateTime.now()).eventId();
        ReservationOutboxClaim claim = new TransactionTemplate(transactionManager).execute(status -> {
            ReservationOutboxClaim result = outboxTransactionUseCase.claimReady(100).stream()
                .filter(candidate -> candidate.message().eventId().equals(eventId)).findFirst().orElseThrow();
            status.setRollbackOnly();
            return result;
        });
        assertThat(jdbcTemplate.queryForObject("SELECT claim_id FROM reservation_job_outbox WHERE event_id = ?", UUID.class, eventId)).isEqualTo(claim.claimId());
        assertThat(outboxTransactionUseCase.complete(claim)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT published_at FROM reservation_job_outbox WHERE event_id = ?", LocalDateTime.class, eventId)).isNotNull();
    }

    @Test
    @DisplayName("입장은 전달 대기 상태와 Outbox를 한 번만 저장하며 실행 lease는 시작하지 않는다")
    void admissionRecordsOneDurableJobWithoutExecutionLease() {
        PurchaseWaitingEntry entry = createWaiting();
        UUID token = UUID.randomUUID();
        PurchaseWaitingEntry dispatched = claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), token)).orElseThrow();
        assertThat(dispatched.getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        assertThat(dispatched.getLeaseUntil()).isNull();
        assertThat(dispatched.getExecutionAttempts()).isZero();
        LocalDateTime outboxCreatedAt = jdbcTemplate.queryForObject("SELECT created_at FROM reservation_job_outbox WHERE waiting_entry_id = ?", LocalDateTime.class, entry.getId());
        assertThat(outboxCreatedAt).isCloseTo(dispatched.getDispatchedAt(), within(1, ChronoUnit.SECONDS));
        assertThat(claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID()))).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation_job_outbox WHERE waiting_entry_id = ?", Long.class, entry.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Outbox 기록이 실패하면 입장 상태와 슬롯 점유도 함께 롤백한다")
    void failedOutboxInsertRollsBackAdmission() {
        PurchaseWaitingEntry entry = createWaiting();
        doThrow(new DataAccessResourceFailureException("outbox storage unavailable")).when(purchaseWaitingOutboxPort).append(any());
        assertThatThrownBy(() -> claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())))
            .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation_job_outbox WHERE waiting_entry_id = ?", Long.class, entry.getId())).isZero();
    }

    @Test
    @DisplayName("발행 실패는 원본 메시지를 유지하고 재시도 시각 이후에만 다시 발행 대상으로 노출한다")
    void publishFailurePreservesTheMessageUntilItsRetryTime() {
        PurchaseWaitingEntry entry = createWaiting();
        UUID token = UUID.randomUUID();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), token)).orElseThrow();
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        ReservationOutboxMessage message = readyMessage(entry.getId(), now);
        assertThat(purchaseWaitingOutboxPort.containsRequest(message.eventId(), entry.getId(), entry.getSaleProductId(), token)).isTrue();
        assertThat(purchaseWaitingOutboxPort.containsRequest(message.eventId(), entry.getId(), entry.getSaleProductId(), UUID.randomUUID())).isFalse();

        ReservationOutboxClaim claim = claimMessage(message.eventId(), now);
        assertThat(purchaseWaitingOutboxPort.recordFailure(message.eventId(), claim.claimId(), now, now.plusSeconds(5), "KAFKA_UNAVAILABLE")).isTrue();

        assertThat(claimMessages(now)).extracting(result -> result.message().eventId()).doesNotContain(message.eventId());
        ReservationOutboxClaim retryClaim = claimMessage(message.eventId(), now.plusSeconds(5));
        ReservationOutboxMessage retried = retryClaim.message();
        assertThat(retried.eventId()).isEqualTo(message.eventId());
        assertThat(retried.topic()).isEqualTo(message.topic());
        assertThat(retried.key()).isEqualTo(entry.getSaleProductId().toString());
        assertThat(retried.payload()).isEqualTo(message.payload());
        assertThat(retried.attempts()).isEqualTo(2);

        assertThat(purchaseWaitingOutboxPort.markPublished(message.eventId(), retryClaim.claimId(), now.plusSeconds(6))).isTrue();
        assertThat(purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), token)).contains(now.plusSeconds(6));
        assertThat(claimMessages(now.plusSeconds(10))).extracting(result -> result.message().eventId()).doesNotContain(message.eventId());
    }

    @Test
    @DisplayName("다른 Publisher가 잠근 메시지는 기다리지 않고 건너뛰며 잠금 해제 후 다시 선택할 수 있다")
    void anotherPublisherSkipsLockedMessagesWithoutLosingThem() throws Exception {
        PurchaseWaitingEntry entry = createWaiting();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        UUID eventId = readyMessage(entry.getId(), now).eventId();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> holding = executorService.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("SELECT event_id FROM reservation_job_outbox WHERE event_id = ? FOR UPDATE", UUID.class, eventId);
                locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Publisher lock release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            List<ReservationOutboxClaim> selected = claimMessages(now);
            assertThat(selected).extracting(result -> result.message().eventId()).doesNotContain(eventId);
            release.countDown();
            holding.get(5, TimeUnit.SECONDS);
            assertThat(claimMessage(eventId, now).message().eventId()).isEqualTo(eventId);
        } finally {
            release.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("기술 실패의 관리자 재처리는 원래 순번과 키를 유지하고 예산을 초기화하며 이전 메시지를 무효화한다")
    void technicalFailureReplayPreservesIdentityAndFencesOldDelivery() throws Exception {
        PurchaseWaitingEntry entry = exhaustedEntry();
        UUID oldToken = entry.getProcessingToken();
        ReservationOutboxMessage original = readyMessage(entry.getId(), LocalDateTime.now().plusSeconds(1));
        entry.failExecution(LocalDateTime.now());
        purchaseWaitingEntryRepository.save(entry);
        replay(entry.getId(), "admin").andExpect(MockMvcResultMatchers.status().isOk());
        PurchaseWaitingEntry replayed = purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(replayed.getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(replayed.getExecutionAttempts()).isZero();
        assertThat(replayed.getProcessingToken()).isNull();
        assertThat(replayed.getQueueSequence()).isEqualTo(entry.getQueueSequence());
        assertThat(replayed.getPurchaseRequestKey()).isEqualTo(entry.getPurchaseRequestKey());
        purchaseWaitingReservationPort.execute(new ReservationJobInput(
            original.eventId(), entry.getId(), entry.getSaleProductId(), oldToken));
        assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(jdbcTemplate.queryForObject("SELECT reserved_quantity FROM inventories WHERE sale_product_id = ?", Long.class, entry.getSaleProductId())).isZero();
    }

    @Test
    @DisplayName("실행 예산을 소진한 만료 lease는 다시 대기시키지 않고 실패와 DLT Outbox로 확정한다")
    void exhaustedLeaseDoesNotStartAnotherAutomaticAttempt() {
        PurchaseWaitingEntry entry = exhaustedEntry();
        jdbcTemplate.update("UPDATE reservation_queue_entries SET lease_until = ? WHERE id = ?", LocalDateTime.now().minusSeconds(1), entry.getId());
        recoverPurchaseWaitingUseCase.recover();
        PurchaseWaitingEntry failed = purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(PurchaseWaitingStatus.FAILED);
        assertThat(failed.getExecutionAttempts()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation_job_outbox WHERE waiting_entry_id = ? AND event_type = 'PurchaseReservationExecutionFailed'", Long.class, entry.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("발행되지 않은 전달 대기와 실행 중 요청은 관리자가 재처리할 수 없다")
    void pendingOrRunningDeliveryCannotBeReplayed() throws Exception {
        PurchaseWaitingEntry waiting = createWaiting();
        PurchaseWaitingEntry entry = claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(waiting.getId(), UUID.randomUUID())).orElseThrow();
        jdbcTemplate.update("UPDATE reservation_queue_entries SET dispatched_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(2), entry.getId());
        replay(entry.getId(), "admin").andExpect(MockMvcResultMatchers.status().isConflict());
        entry.beginProcessing(entry.getProcessingToken(), LocalDateTime.now(), LocalDateTime.now().plusSeconds(30));
        purchaseWaitingEntryRepository.save(entry);
        replay(entry.getId(), "admin").andExpect(MockMvcResultMatchers.status().isConflict());
        assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getExecutionAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("재처리 API는 관리자 인증이 없으면 401 또는 403으로 거부한다")
    void replayRequiresAdminAuthentication() throws Exception {
        PurchaseWaitingEntry entry = createWaiting();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/queue-entries/{id}/replay", entry.getId()))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/queue-entries/{id}/replay", entry.getId())
                .header("X-Member-Id", entry.getMemberId()))
            .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @DisplayName("같은 실패 요청을 동시에 재처리해도 한 요청만 대기로 전환한다")
    void concurrentReplaysHaveOneWinner() throws Exception {
        PurchaseWaitingEntry entry = exhaustedEntry();
        entry.failExecution(LocalDateTime.now());
        purchaseWaitingEntryRepository.save(entry);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Replay start timed out");
                    }
                    return replay(entry.getId(), "admin").andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(results.get(0).get(5, TimeUnit.SECONDS), results.get(1).get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("기술 실패 뒤 같은 회원과 상품에 다른 활성 요청이 생겼으면 재처리로 중복 대기를 만들지 않는다")
    void replayCannotReactivateAlongsideAnotherActiveRequest() throws Exception {
        PurchaseWaitingEntry entry = exhaustedEntry();
        entry.failExecution(LocalDateTime.now());
        purchaseWaitingEntryRepository.save(entry);
        purchaseWaitingEntryRepository.save(PurchaseWaitingEntry.enqueue(UUID.randomUUID(), entry.getMemberId(),
            entry.getSaleProductId(), "another-key-" + UUID.randomUUID(), entry.getQueueSequence() + 1, LocalDateTime.now()));
        replay(entry.getId(), "admin").andExpect(status().isConflict());
        PurchaseWaitingEntry failed = purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(PurchaseWaitingStatus.FAILED);
        assertThat(failed.getExecutionAttempts()).isEqualTo(3);
        assertThat(purchaseWaitingEntryRepository.countActive(entry.getSaleProductId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Kafka 전달 대기 상태는 조회와 멱등 재접수 API에서 기존 PROCESSING으로 표시한다")
    void dispatchedStatePreservesThePublicProcessingContract() throws Exception {
        PurchaseWaitingEntry waiting = createWaiting();
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(waiting.getId(), UUID.randomUUID())).orElseThrow();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/queue-entries/{id}", waiting.getId())
                .header("X-Member-Id", waiting.getMemberId()))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("PROCESSING"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sale-products/{id}/queue-entries", waiting.getSaleProductId())
                .header("X-Member-Id", waiting.getMemberId()).header("Idempotency-Key", waiting.getPurchaseRequestKey()))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("PROCESSING"));
        assertThat(purchaseWaitingEntryRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation_job_outbox WHERE waiting_entry_id = ?", Long.class, waiting.getId())).isEqualTo(1L);
    }

    private ResultActions replay(UUID entryId, String admin) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/queue-entries/{id}/replay", entryId)
            .header("X-Admin-Id", admin));
    }

    private PurchaseWaitingEntry exhaustedEntry() {
        PurchaseWaitingEntry waiting = createWaiting();
        PurchaseWaitingEntry entry = claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(waiting.getId(), UUID.randomUUID())).orElseThrow();
        for (int attempt = 0; attempt < 3; attempt++) {
            entry.beginProcessing(entry.getProcessingToken(), LocalDateTime.now(), LocalDateTime.now().plusSeconds(30));
            if (attempt < 2) {
                entry.retryDispatched();
            }
        }
        return purchaseWaitingEntryRepository.save(entry);
    }

    private ReservationOutboxMessage readyMessage(UUID entryId, LocalDateTime now) {
        return jdbcTemplate.queryForObject("SELECT event_id, topic, message_key, payload, publish_attempts FROM reservation_job_outbox WHERE waiting_entry_id = ?",
            (rs, row) -> new ReservationOutboxMessage(rs.getObject("event_id", UUID.class), rs.getString("topic"), rs.getString("message_key"), rs.getString("payload"), rs.getInt("publish_attempts")), entryId);
    }

    private List<ReservationOutboxClaim> claimMessages(LocalDateTime now) {
        return new TransactionTemplate(transactionManager).execute(status ->
            purchaseWaitingOutboxPort.claimReady(UUID.randomUUID(), now, now.plusSeconds(30), 100));
    }

    private ReservationOutboxClaim claimMessage(UUID eventId, LocalDateTime now) {
        return claimMessages(now).stream().filter(claim -> claim.message().eventId().equals(eventId)).findFirst().orElseThrow();
    }

    private PurchaseWaitingEntry createWaiting() {
        Member member = memberRepository.save(Member.register("outbox-" + UUID.randomUUID(), "대기 회원"));
        Product product = productRepository.save(Product.create("Outbox 상품", "예약"));
        LocalDateTime now = LocalDateTime.now();
        SaleProduct sale = SaleProduct.createDraft(product.getId(), Money.of(BigDecimal.TEN), SalePeriod.of(now.minusMinutes(1), now.plusHours(1)));
        sale.open(now);
        sale = saleProductRepository.save(sale);
        inventoryRepository.save(Inventory.initialize(sale.getId(), 1));
        return purchaseWaitingEntryRepository.save(PurchaseWaitingEntry.enqueue(UUID.randomUUID(), member.getId(), sale.getId(), "key-" + UUID.randomUUID(), 1, now));
    }
}
