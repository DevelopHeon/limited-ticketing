package com.example.limitedticketing.integration.purchasewaiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandPort;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingReservationPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingWorkerPort;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobInput;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.dto.ReservationJobOutcome;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingQueuePort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.PurchaseWaitingClaim;
import com.example.limitedticketing.purchasewaiting.application.service.DispatchPurchaseWaitingService;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.RecoverPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ReleasePurchaseWaitingSlotUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.StartPurchaseWaitingExecutionUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ProcessPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReleasePurchaseWaitingSlotCommand;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ReservationExecutionCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "spring.datasource.hikari.connection-timeout=1000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("구매 대기 접수와 처리 및 장애 복구 통합 검증")
class PurchaseWaitingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PurchaseWaitingWorkerPort purchaseWaitingWorkerPort;

    @Autowired
    private ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase;

    @Autowired
    private RecoverPurchaseWaitingUseCase recoverPurchaseWaitingUseCase;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleProductRepository saleProductRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PurchaseCommandPort purchaseCommandPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PurchaseWaitingQueuePort purchaseWaitingQueuePort;
    @MockitoSpyBean
    private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Autowired
    private ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase;
    @Autowired
    private PurchaseWaitingReservationPort purchaseWaitingReservationPort;
    @Autowired
    private StartPurchaseWaitingExecutionUseCase startPurchaseWaitingExecutionUseCase;
    @Autowired
    private ReleasePurchaseWaitingSlotUseCase releasePurchaseWaitingSlotUseCase;

    @Autowired
    private PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", postgresql::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", postgresql::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", postgresql::getPassword);
        dynamicPropertyRegistry.add("spring.data.redis.host", redis::getHost);
        dynamicPropertyRegistry.add("spring.data.redis.port", redis::getFirstMappedPort);
        dynamicPropertyRegistry.add("limited-ticketing.queue.worker.initial-delay", () -> "600000");
        dynamicPropertyRegistry.add("limited-ticketing.queue.worker.recovery-initial-delay", () -> "600000");
        dynamicPropertyRegistry.add("limited-ticketing.queue.worker.recovery-batch-size", () -> "2");
    }

    @BeforeEach
    void clearQueueFixtures() {
        jdbcTemplate.update("DELETE FROM reservation_job_outbox");
        jdbcTemplate.update("DELETE FROM reservation_queue_entries");
        try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    @DisplayName("Redis 순번 키가 유실되어도 새 대기는 기존 원장 순번 뒤에 배치된다")
    void sequenceSurvivesRedisCounterLoss() throws Exception {
        Fixture fixture = createFixture(2);
        QueueEntry first = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        stringRedisTemplate.delete("sequence:sale:" + fixture.saleProductId());
        QueueEntry second = enqueue(fixture.saleProductId(), fixture.secondMemberId(), issuePurchaseKey(fixture.secondMemberId()));
        Long firstSequence = jdbcTemplate.queryForObject(
            "select queue_sequence from reservation_queue_entries where id = ?", Long.class, first.id());
        Long secondSequence = jdbcTemplate.queryForObject(
            "select queue_sequence from reservation_queue_entries where id = ?", Long.class, second.id());
        assertThat(secondSequence).isGreaterThan(firstSequence);
    }

    @Test
    @DisplayName("순번 동점이 복구 배치 경계에 있어도 모든 대기를 빠짐없이 복원한다")
    void equalSequencesDoNotSkipTheNextRecoveryPage() throws Exception {
        Fixture fixture = createFixture(3);
        Member third = memberRepository.save(Member.register("queue-tie-" + System.nanoTime(), "동점 회원"));
        List<QueueEntry> entries = List.of(
            enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId())),
            enqueue(fixture.saleProductId(), fixture.secondMemberId(), issuePurchaseKey(fixture.secondMemberId())),
            enqueue(fixture.saleProductId(), third.getId(), issuePurchaseKey(third.getId()))
        );
        jdbcTemplate.update("update reservation_queue_entries set queue_sequence = 1 where sale_product_id = ?", fixture.saleProductId());
        String waiting = "waiting:sale:" + fixture.saleProductId();
        stringRedisTemplate.delete(waiting);
        recoverPurchaseWaitingUseCase.recover();
        assertThat(stringRedisTemplate.opsForZSet().zCard(waiting)).isEqualTo(3);
        entries.forEach(entry -> assertThat(stringRedisTemplate.opsForZSet().rank(waiting, entry.id().toString())).isNotNull());
    }

    @Test
    @DisplayName("여러 Worker가 같은 요청을 동시에 claim해도 처리 권한은 하나만 발급된다")
    void concurrentClaimsHaveOneWinner() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<PurchaseWaitingEntry>>> results = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                results.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent claim start timed out");
                    }
                    return claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.id(), UUID.randomUUID()));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int winners = 0;
            for (Future<Optional<PurchaseWaitingEntry>> result : results) {
                if (result.get(5, TimeUnit.SECONDS).isPresent()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("대기 접수는 멱등하며 예약과 품절 결과가 실제 재고 수량과 일치한다")
    void queuesRequestsIdempotentlyAndProcessesOnlyTheInventoryWinner() throws Exception {
        Fixture fixture = createFixture(1);
        String firstRequestKey = issuePurchaseKey(fixture.firstMemberId());
        String secondRequestKey = issuePurchaseKey(fixture.secondMemberId());

        QueueEntry firstEntry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), firstRequestKey);
        QueueEntry retriedFirstEntry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), firstRequestKey);
        QueueEntry secondEntry = enqueue(fixture.saleProductId(), fixture.secondMemberId(), secondRequestKey);

        assertThat(retriedFirstEntry.id()).isEqualTo(firstEntry.id());
        assertThat(firstEntry.status()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(firstEntry.position()).isEqualTo(1);
        assertThat(secondEntry.position()).isEqualTo(2);

        purchaseWaitingWorkerPort.dispatchWaitingEntries();

        QueueEntry completedFirstEntry = awaitCompletion(firstEntry.id(), fixture.firstMemberId());
        QueueEntry completedSecondEntry = awaitCompletion(secondEntry.id(), fixture.secondMemberId());

        assertThat(List.of(completedFirstEntry.status(), completedSecondEntry.status()))
            .containsExactlyInAnyOrder(PurchaseWaitingStatus.RESERVED, PurchaseWaitingStatus.SOLD_OUT);
        assertThat(purchaseRepository.findById(
            completedFirstEntry.status() == PurchaseWaitingStatus.RESERVED
                ? completedFirstEntry.purchaseId()
                : completedSecondEntry.purchaseId()
        )).isPresent();

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isZero()
        );
    }

    @Test
    @DisplayName("같은 회원과 상품에 활성 대기 요청을 중복 생성할 수 없다")
    void rejectsAnotherActiveQueueEntryForTheSameMemberAndSaleProduct() throws Exception {
        Fixture fixture = createFixture(2);

        enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));

        stringRedisTemplate.delete("admission:sale:" + fixture.saleProductId() + ":member:" + fixture.firstMemberId());
        mockMvc.perform(post("/api/sale-products/{saleProductId}/queue-entries", fixture.saleProductId())
                .header("X-Member-Id", fixture.firstMemberId())
                .header("Idempotency-Key", issuePurchaseKey(fixture.firstMemberId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ACTIVE_QUEUE_ENTRY_EXISTS"));
    }

    @Test
    @DisplayName("다른 회원의 구매 대기 상태 조회를 거부한다")
    void anotherMemberCannotReadTheWaitingRequest() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), "waiting-private");

        mockMvc.perform(get("/api/queue-entries/{queueEntryId}", entry.id())
                .header("X-Member-Id", fixture.secondMemberId()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("QUEUE_ENTRY_OWNERSHIP_VIOLATION"));
        assertThat(find(entry.id(), fixture.firstMemberId()).status()).isEqualTo(PurchaseWaitingStatus.WAITING);
    }

    @Test
    @DisplayName("구매 대기 요청 키를 다른 판매 상품에 재사용할 수 없다")
    void requestKeyCannotBeReusedForAnotherSaleProduct() throws Exception {
        Fixture firstSale = createFixture(1);
        Fixture secondSale = createFixture(1);
        enqueue(firstSale.saleProductId(), firstSale.firstMemberId(), "waiting-key-reuse");

        mockMvc.perform(post("/api/sale-products/{saleProductId}/queue-entries", secondSale.saleProductId())
                .header("X-Member-Id", firstSale.firstMemberId())
                .header("Idempotency-Key", "waiting-key-reuse"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("QUEUE_REQUEST_KEY_REUSE"));
        assertThat(stringRedisTemplate.opsForZSet().zCard("waiting:sale:" + secondSale.saleProductId())).isZero();
    }

    @Test
    @DisplayName("처리 전에 판매가 종료되면 대기 요청을 실패로 기록하고 재고를 예약하지 않는다")
    void saleClosingBeforeProcessingFailsTheRequestWithoutReservingInventory() throws Exception {
        Fixture fixture = createFixture(1);
        String requestKey = "waiting-closed-sale";
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), requestKey);
        SaleProduct sale = saleProductRepository.findById(fixture.saleProductId()).orElseThrow();
        sale.close();
        saleProductRepository.save(sale);

        purchaseWaitingWorkerPort.dispatchWaitingEntries();

        QueueEntry completed = awaitCompletion(entry.id(), fixture.firstMemberId());
        assertThat(completed.status()).isEqualTo(PurchaseWaitingStatus.FAILED);
        mockMvc.perform(get("/api/queue-entries/{queueEntryId}", entry.id())
                .header("X-Member-Id", fixture.firstMemberId()))
            .andExpect(jsonPath("$.data.failureCode").value("SALE_UNAVAILABLE"));
        assertThat(purchaseRepository.findByMemberIdAndPurchaseRequestKey(fixture.firstMemberId(), requestKey)).isEmpty();
        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("구매 커밋 후 대기 처리 중단을 복구해도 같은 구매를 재사용하고 재고를 반복 예약하지 않는다")
    void recoveryAfterPurchaseCommitReusesThePurchaseWithoutReservingInventoryAgain() throws Exception {
        Fixture fixture = createFixture(2);
        String requestKey = "waiting-purchase-committed";
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), requestKey);
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.id(), UUID.randomUUID())).orElseThrow();
        String waitingKey = "waiting:sale:" + fixture.saleProductId();
        String processingKey = "processing:sale:" + fixture.saleProductId();
        stringRedisTemplate.opsForZSet().remove(waitingKey, entry.id().toString());
        stringRedisTemplate.opsForZSet().add(processingKey, entry.id().toString(), 0);
        PurchaseReservation committedPurchase = purchaseCommandPort.reserve(
            new ReservePurchaseInput(fixture.firstMemberId(), fixture.saleProductId(), requestKey)
        );

        startDispatchedEntries();
        jdbcTemplate.update("update reservation_queue_entries set lease_until = ? where id = ?",
            LocalDateTime.of(2000, 1, 1, 0, 0), entry.id());
        recoverPurchaseWaitingUseCase.recover();
        purchaseWaitingWorkerPort.dispatchWaitingEntries();

        QueueEntry recoveredEntry = awaitCompletion(entry.id(), fixture.firstMemberId());
        assertThat(recoveredEntry.status()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(recoveredEntry.purchaseId()).isEqualTo(committedPurchase.purchaseId());
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from purchases where member_id = ? and purchase_request_key = ?",
            Long.class, fixture.firstMemberId(), requestKey
        )).isEqualTo(1);
        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("처리 제한 시간을 넘긴 요청은 대기 상태와 원래 순번으로 복구한다")
    void recoversAnExpiredProcessingEntryBackToTheWaitingQueue() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(
            fixture.saleProductId(),
            fixture.firstMemberId(),
            issuePurchaseKey(fixture.firstMemberId())
        );
        String waitingKey = "waiting:sale:" + fixture.saleProductId();
        String processingKey = "processing:sale:" + fixture.saleProductId();

        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.id(), UUID.randomUUID())).orElseThrow();
        stringRedisTemplate.opsForZSet().remove(waitingKey, entry.id().toString());
        stringRedisTemplate.opsForZSet().add(processingKey, entry.id().toString(), 0);

        startDispatchedEntries();
        jdbcTemplate.update("update reservation_queue_entries set lease_until = ? where id = ?",
            LocalDateTime.of(2000, 1, 1, 0, 0), entry.id());
        recoverPurchaseWaitingUseCase.recover();

        QueueEntry recoveredEntry = find(entry.id(), fixture.firstMemberId());
        assertThat(recoveredEntry.status()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(stringRedisTemplate.opsForZSet().rank(waitingKey, entry.id().toString())).isZero();
        assertThat(stringRedisTemplate.opsForZSet().rank(processingKey, entry.id().toString())).isNull();
    }

    @Test
    @DisplayName("Redis에서 가져온 요청이 DB에 없으면 처리 대상에서 제외한다")
    void returnsEmptyWhenClaimedRedisEntryDoesNotExistInDatabase() {
        assertThat(claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(UUID.randomUUID(), UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("복구 배치보다 많은 대기 요청을 커서로 빠짐없이 복원한다")
    void recoversEveryWaitingEntryUsingTheKeysetCursor() throws Exception {
        Fixture fixture = createFixture(3);
        Member thirdMember = memberRepository.save(Member.register(
            "queue-member-" + System.nanoTime(),
            "세 번째 회원"
        ));
        List<QueueEntry> entries = List.of(
            enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId())),
            enqueue(fixture.saleProductId(), fixture.secondMemberId(), issuePurchaseKey(fixture.secondMemberId())),
            enqueue(fixture.saleProductId(), thirdMember.getId(), issuePurchaseKey(thirdMember.getId()))
        );
        String waitingKey = "waiting:sale:" + fixture.saleProductId();
        stringRedisTemplate.delete(waitingKey);

        recoverPurchaseWaitingUseCase.recover();

        entries.forEach(entry -> assertThat(
            stringRedisTemplate.opsForZSet().rank(waitingKey, entry.id().toString())
        ).isNotNull());
    }

    @Test
    @DisplayName("DB에 없는 Redis 대기 및 처리 항목을 제거한다")
    void removesWaitingAndProcessingEntriesThatDoNotExistInDatabase() {
        Fixture fixture = createFixture(1);
        UUID missingWaitingEntryId = UUID.randomUUID();
        UUID missingProcessingEntryId = UUID.randomUUID();
        String waitingKey = "waiting:sale:" + fixture.saleProductId();
        String processingKey = "processing:sale:" + fixture.saleProductId();
        stringRedisTemplate.opsForZSet().add(waitingKey, missingWaitingEntryId.toString(), 1);
        stringRedisTemplate.opsForZSet().add(
            processingKey,
            missingProcessingEntryId.toString(),
            System.currentTimeMillis()
        );

        recoverPurchaseWaitingUseCase.recover();

        assertThat(stringRedisTemplate.opsForZSet().rank(waitingKey, missingWaitingEntryId.toString())).isNull();
        assertThat(stringRedisTemplate.opsForZSet().rank(processingKey, missingProcessingEntryId.toString())).isNull();
    }

    @Test
    @DisplayName("판매 상품이 없으면 남은 Redis 대기열 키를 정리한다")
    void deletesRedisKeysWhenTheSaleProductNoLongerExists() {
        long missingSaleProductId = Long.MAX_VALUE;
        stringRedisTemplate.opsForValue().set("sequence:sale:" + missingSaleProductId, "10");
        stringRedisTemplate.opsForZSet().add(
            "waiting:sale:" + missingSaleProductId,
            UUID.randomUUID().toString(),
            1
        );

        recoverPurchaseWaitingUseCase.recover();

        assertThat(stringRedisTemplate.hasKey("waiting:sale:" + missingSaleProductId)).isFalse();
        assertThat(stringRedisTemplate.hasKey("processing:sale:" + missingSaleProductId)).isFalse();
        assertThat(stringRedisTemplate.hasKey("sequence:sale:" + missingSaleProductId)).isFalse();
    }

    @Test
    @DisplayName("종료된 판매에 활성 대기가 없으면 모든 Redis 대기열 키를 정리한다")
    void deletesAllRedisKeysWhenClosedSaleHasNoActiveQueueEntry() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(
            fixture.saleProductId(),
            fixture.firstMemberId(),
            issuePurchaseKey(fixture.firstMemberId())
        );
        purchaseWaitingWorkerPort.dispatchWaitingEntries();
        awaitCompletion(entry.id(), fixture.firstMemberId());

        SaleProduct saleProduct = saleProductRepository.findById(fixture.saleProductId()).orElseThrow();
        saleProduct.close();
        saleProductRepository.save(saleProduct);

        recoverPurchaseWaitingUseCase.recover();

        assertThat(stringRedisTemplate.hasKey("waiting:sale:" + fixture.saleProductId())).isFalse();
        assertThat(stringRedisTemplate.hasKey("processing:sale:" + fixture.saleProductId())).isFalse();
        assertThat(stringRedisTemplate.hasKey("sequence:sale:" + fixture.saleProductId())).isFalse();
    }

    @Test
    @DisplayName("예약 확정 후 Redis 슬롯 반환이 실패해도 구매와 재고를 보존하고 복구 시 슬롯만 정리한다")
    void completedReservationSurvivesSlotReleaseFailure() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        purchaseWaitingWorkerPort.dispatchWaitingEntries();
        ReservationJobInput input = jobInput(entry.id());
        doThrow(new RedisConnectionFailureException("슬롯 반환 실패"))
            .doCallRealMethod().when(purchaseWaitingQueuePort)
            .releaseProcessing(input.saleProductId(), input.waitingEntryId(), input.processingToken());

        assertThat(purchaseWaitingReservationPort.execute(input)).isEqualTo(ReservationJobOutcome.COMPLETED);
        assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isEqualTo(1);
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getReservedQuantity()).isEqualTo(1);

        recoverPurchaseWaitingUseCase.recover();
        assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isZero();
        assertThat(purchaseWaitingReservationPort.execute(input)).isEqualTo(ReservationJobOutcome.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("select count(*) from purchases where sale_product_id = ?",
            Long.class, fixture.saleProductId())).isEqualTo(1);
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getReservedQuantity()).isEqualTo(1);
        assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getExecutionAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 이전 Worker는 새 시도의 슬롯을 해제하거나 구매를 중복 예약할 수 없다")
    void staleWorkerCannotReleaseOrRequeueTheNewAttempt() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        PurchaseWaitingClaim first = purchaseWaitingQueuePort.claimNext(fixture.saleProductId(), 10, 1, Instant.now()).get(0);
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.id(), first.token())).orElseThrow();
        startDispatchedEntries();
        jdbcTemplate.update("update reservation_queue_entries set lease_until = ? where id = ?",
            LocalDateTime.of(2000, 1, 1, 0, 0), entry.id());
        recoverPurchaseWaitingUseCase.recover();
        PurchaseWaitingEntry waiting = purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow();
        PurchaseWaitingClaim second = purchaseWaitingQueuePort.claimNext(fixture.saleProductId(), 10, 1, Instant.now()).get(0);
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.id(), second.token())).orElseThrow();

        assertThat(processPurchaseWaitingUseCase.process(new ProcessPurchaseWaitingCommand(entry.id(), first.token()))).isEmpty();
        releasePurchaseWaitingSlotUseCase.release(new ReleasePurchaseWaitingSlotCommand(fixture.saleProductId(), entry.id(), first.token()));
        purchaseWaitingQueuePort.requeue(waiting, first.token());

        assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getProcessingToken()).isEqualTo(second.token());
        assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isEqualTo(1);
        assertThat(stringRedisTemplate.opsForZSet().zCard("waiting:sale:" + fixture.saleProductId())).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from purchases where sale_product_id = ?",
            Long.class, fixture.saleProductId())).isZero();
        startDispatchedEntries();
        PurchaseWaitingEntry completed = processPurchaseWaitingUseCase.process(new ProcessPurchaseWaitingCommand(entry.id(), second.token())).orElseThrow();
        releasePurchaseWaitingSlotUseCase.release(new ReleasePurchaseWaitingSlotCommand(fixture.saleProductId(), entry.id(), second.token()));
        assertThat(completed.getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("lease가 만료되어도 실행 중인 DB 행 잠금이 있으면 복구가 처리 권한을 빼앗지 않는다")
    void recoverySkipsAnExpiredAttemptWhoseExecutionStillHoldsTheRowLock() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        PurchaseWaitingClaim claim = purchaseWaitingQueuePort.claimNext(fixture.saleProductId(), 10, 1, Instant.now()).get(0);
        claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.id(), claim.token())).orElseThrow();
        startDispatchedEntries();
        jdbcTemplate.update("update reservation_queue_entries set lease_until = ? where id = ?",
            LocalDateTime.of(2000, 1, 1, 0, 0), entry.id());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = executorService.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                purchaseWaitingEntryRepository.findByIdForUpdate(entry.id()).orElseThrow();
                locked.countDown();
                awaitLatch(release);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            recoverPurchaseWaitingUseCase.recover();
            assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().ownsAttempt(claim.token())).isTrue();
            assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isEqualTo(1);
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            recoverPurchaseWaitingUseCase.recover();
            assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
            assertThat(purchaseWaitingQueuePort.findPosition(fixture.saleProductId(), entry.id())).isPresent();
        } finally {
            release.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("전역 입장 슬롯이 Kafka 전달 대기로 가득 차면 다른 상품의 DB와 Redis 대기를 claim하지 않는다")
    void fullAdmissionLeavesEveryOtherSaleWaiting() throws Exception {
        Fixture occupied = createFixture(10);
        for (int index = 0; index < 10; index++) {
            Member member = memberRepository.save(Member.register("occupied-" + System.nanoTime(), "전달 대기 회원"));
            QueueEntry admitted = enqueue(occupied.saleProductId(), member.getId(), issuePurchaseKey(member.getId()));
            claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(admitted.id(), UUID.randomUUID())).orElseThrow();
        }
        Fixture first = createFixture(1);
        Fixture second = createFixture(1);
        QueueEntry firstEntry = enqueue(first.saleProductId(), first.firstMemberId(), issuePurchaseKey(first.firstMemberId()));
        QueueEntry secondEntry = enqueue(second.saleProductId(), second.firstMemberId(), issuePurchaseKey(second.firstMemberId()));
        purchaseWaitingWorkerPort.dispatchWaitingEntries();
        assertThat(purchaseWaitingEntryRepository.countInFlight()).isEqualTo(10);
        for (QueueEntry entry : List.of(firstEntry, secondEntry)) {
            PurchaseWaitingEntry persisted = purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
            assertThat(purchaseWaitingQueuePort.findPosition(persisted.getSaleProductId(), entry.id())).isPresent();
            assertThat(purchaseWaitingEntryRepository.countInFlight(persisted.getSaleProductId())).isZero();
        }
    }

    @Test
    @DisplayName("전역 슬롯 전체를 동시에 처리해도 내부 예약 트랜잭션이 DB 연결 부족으로 멈추지 않는다")
    void everyWorkerCanAcquireItsNestedPurchaseConnection() throws Exception {
        Fixture fixture = createFixture(10);
        List<QueueEntry> entries = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Member member = memberRepository.save(Member.register("full-worker-" + System.nanoTime(), "동시 처리 회원"));
            entries.add(enqueue(fixture.saleProductId(), member.getId(), issuePurchaseKey(member.getId())));
        }
        doReturn(List.of(fixture.saleProductId())).when(purchaseWaitingEntryRepository).findSaleProductIdsWithWaitingEntries();
        CountDownLatch outerTransactions = new CountDownLatch(10);
        doAnswer(invocation -> {
            Object locked = invocation.callRealMethod();
            outerTransactions.countDown();
            awaitLatch(outerTransactions);
            return locked;
        }).when(purchaseWaitingEntryRepository).findByIdForUpdate(any(UUID.class));
        purchaseWaitingWorkerPort.dispatchWaitingEntries();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (QueueEntry entry : entries) {
                results.add(executorService.submit(() -> purchaseWaitingReservationPort.execute(jobInput(entry.id()))));
            }
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
            for (QueueEntry entry : entries) {
                assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.RESERVED);
            }
        } finally {
            executorService.shutdownNow();
        }
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Redis claim 뒤 DB 입장이 거부되면 원래 식별자와 순번으로 다시 대기시킨다")
    void rejectedAdmissionRestoresTheOriginalWaitingEntry() throws Exception {
        Fixture fixture = createFixture(1);
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        PurchaseWaitingEntry original = purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow();
        ClaimPurchaseWaitingUseCase rejectedAdmission = command -> Optional.empty();
        DispatchPurchaseWaitingService service = new DispatchPurchaseWaitingService(purchaseWaitingEntryRepository,
            purchaseWaitingQueuePort, rejectedAdmission, Clock.systemDefaultZone(), 10, 10, 1);
        service.dispatchWaitingEntries();
        PurchaseWaitingEntry restored = purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(restored.getProcessingToken()).isNull();
        assertThat(restored.getQueueSequence()).isEqualTo(original.getQueueSequence());
        assertThat(purchaseWaitingQueuePort.findPosition(fixture.saleProductId(), entry.id())).isPresent();
        assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isZero();
    }

    @Test
    @DisplayName("DB 커밋 뒤 Redis 등록이 실패해도 202와 원장을 유지하고 복구 시 다시 대기시킨다")
    void committedAdmissionSurvivesRedisEnqueueFailure() throws Exception {
        Fixture fixture = createFixture(1);
        doThrow(new RedisConnectionFailureException("Deliberate post-commit outage"))
            .when(purchaseWaitingQueuePort).enqueue(any(PurchaseWaitingEntry.class));
        QueueEntry entry = enqueue(fixture.saleProductId(), fixture.firstMemberId(), issuePurchaseKey(fixture.firstMemberId()));
        assertThat(purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        assertThat(purchaseWaitingQueuePort.findPosition(fixture.saleProductId(), entry.id())).isEmpty();
        doCallRealMethod().when(purchaseWaitingQueuePort).enqueue(any(PurchaseWaitingEntry.class));
        recoverPurchaseWaitingUseCase.recover();
        assertThat(purchaseWaitingQueuePort.findPosition(fixture.saleProductId(), entry.id())).isPresent();
    }

    @Test
    @DisplayName("Redis 처리 슬롯이 유실되어도 DB의 상품별 처리 상한은 초과하지 않는다")
    void databaseProcessingLimitSurvivesRedisSlotLoss() throws Exception {
        Fixture fixture = createFixture(10);
        List<QueueEntry> entries = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            Member member = memberRepository.save(Member.register("lost-slot-" + System.nanoTime(), "슬롯 유실 회원"));
            entries.add(enqueue(fixture.saleProductId(), member.getId(), issuePurchaseKey(member.getId())));
        }
        List<PurchaseWaitingClaim> claimed = purchaseWaitingQueuePort.claimNext(fixture.saleProductId(), 10, 10, Instant.now());
        assertThat(claimed).hasSize(10);
        for (PurchaseWaitingClaim claim : claimed) {
            claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(claim.waitingEntryId(), claim.token())).orElseThrow();
        }
        stringRedisTemplate.delete(List.of("processing:sale:" + fixture.saleProductId(),
            "processing-tokens:sale:" + fixture.saleProductId()));
        PurchaseWaitingClaim extra = purchaseWaitingQueuePort.claimNext(fixture.saleProductId(), 10, 1, Instant.now()).get(0);
        assertThat(claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(extra.waitingEntryId(), extra.token()))).isEmpty();
        assertThat(purchaseWaitingEntryRepository.countInFlight(fixture.saleProductId())).isEqualTo(10);
        PurchaseWaitingEntry waiting = purchaseWaitingEntryRepository.findById(extra.waitingEntryId()).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(PurchaseWaitingStatus.WAITING);
        purchaseWaitingQueuePort.requeue(waiting, extra.token());
        startDispatchedEntries();
        jdbcTemplate.update("update reservation_queue_entries set lease_until = ? where sale_product_id = ? and status = 'PROCESSING'",
            LocalDateTime.of(2000, 1, 1, 0, 0), fixture.saleProductId());
        recoverPurchaseWaitingUseCase.recover();
        for (QueueEntry entry : entries) {
            Long memberId = purchaseWaitingEntryRepository.findById(entry.id()).orElseThrow().getMemberId();
            awaitCompletion(entry.id(), memberId);
        }
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getReservedQuantity()).isEqualTo(10);
    }

    private ReservationJobInput jobInput(UUID entryId) {
        return jdbcTemplate.queryForObject("""
            SELECT event_id, waiting_entry_id, sale_product_id, processing_token FROM reservation_job_outbox
            WHERE waiting_entry_id = ? AND event_type = 'PurchaseReservationRequested'
            ORDER BY created_at DESC LIMIT 1
            """, (rs, row) -> new ReservationJobInput(rs.getObject("event_id", UUID.class),
            rs.getObject("waiting_entry_id", UUID.class), rs.getLong("sale_product_id"), rs.getObject("processing_token", UUID.class)), entryId);
    }

    private void startDispatchedEntries() {
        for (UUID entryId : jdbcTemplate.queryForList("SELECT id FROM reservation_queue_entries WHERE status = 'DISPATCHED'", UUID.class)) {
            ReservationJobInput input = jobInput(entryId);
            startPurchaseWaitingExecutionUseCase.start(new ReservationExecutionCommand(input.eventId(), input.waitingEntryId(), input.saleProductId(), input.processingToken()));
        }
    }

    private void consumeDispatchedEntries() {
        for (UUID entryId : jdbcTemplate.queryForList("SELECT id FROM reservation_queue_entries WHERE status = 'DISPATCHED'", UUID.class)) {
            purchaseWaitingReservationPort.execute(jobInput(entryId));
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private QueueEntry enqueue(Long saleProductId, Long memberId, String requestKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sale-products/{saleProductId}/queue-entries", saleProductId)
                .header("X-Member-Id", memberId)
                .header("Idempotency-Key", requestKey))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new QueueEntry(
            UUID.fromString(data.path("queueEntryId").asText()),
            PurchaseWaitingStatus.valueOf(data.path("status").asText()),
            data.path("position").isNull() ? null : data.path("position").asLong(),
            data.path("purchaseId").isNull() ? null : data.path("purchaseId").asLong()
        );
    }

    private QueueEntry awaitCompletion(UUID queueEntryId, Long memberId) throws Exception {
        for (int attempts = 0; attempts < 50; attempts++) {
            purchaseWaitingWorkerPort.dispatchWaitingEntries();
            consumeDispatchedEntries();
            QueueEntry entry = find(queueEntryId, memberId);
            if (entry.status() != PurchaseWaitingStatus.WAITING && entry.status() != PurchaseWaitingStatus.PROCESSING) {
                return entry;
            }
            Thread.sleep(50);
        }

        throw new AssertionError("Queue entry did not complete in time");
    }

    private QueueEntry find(UUID queueEntryId, Long memberId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/queue-entries/{queueEntryId}", queueEntryId)
                .header("X-Member-Id", memberId))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new QueueEntry(
            queueEntryId,
            PurchaseWaitingStatus.valueOf(data.path("status").asText()),
            data.path("position").isNull() ? null : data.path("position").asLong(),
            data.path("purchaseId").isNull() ? null : data.path("purchaseId").asLong()
        );
    }

    private String issuePurchaseKey(Long memberId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/idempotency-keys")
                .header("X-Member-Id", memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"PURCHASE\"}"))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data")
            .path("idempotencyKey")
            .asText();
    }

    private Fixture createFixture(long inventoryQuantity) {
        Member firstMember = memberRepository.save(Member.register("queue-member-" + System.nanoTime(), "첫 번째 회원"));
        Member secondMember = memberRepository.save(Member.register("queue-member-" + System.nanoTime(), "두 번째 회원"));
        Product product = productRepository.save(Product.create("대기열 테스트 상품", "대기열 테스트 혜택"));
        LocalDateTime now = LocalDateTime.now();
        SaleProduct saleProduct = SaleProduct.createDraft(
            product.getId(),
            Money.of(BigDecimal.valueOf(10_000)),
            SalePeriod.of(now.minusMinutes(1), now.plusHours(1))
        );
        saleProduct.open(now);
        saleProduct = saleProductRepository.save(saleProduct);
        Inventory inventory = inventoryRepository.save(Inventory.initialize(saleProduct.getId(), inventoryQuantity));

        return new Fixture(firstMember.getId(), secondMember.getId(), saleProduct.getId(), inventory.getId());
    }

    private record Fixture(Long firstMemberId, Long secondMemberId, Long saleProductId, Long inventoryId) {
    }

    private record QueueEntry(UUID id, PurchaseWaitingStatus status, Long position, Long purchaseId) {
    }
}
