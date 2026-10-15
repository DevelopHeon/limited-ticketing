package com.example.limitedticketing.integration.purchasewaiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandFacade;
import com.example.limitedticketing.purchase.application.port.in.dto.PurchaseReservation;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchasewaiting.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueClaimService;
import com.example.limitedticketing.purchasewaiting.application.command.service.ReservationQueueRecoveryService;
import com.example.limitedticketing.purchasewaiting.infrastructure.scheduler.ReservationQueueWorker;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("구매 대기 접수와 처리 및 장애 복구 통합 검증")
class ReservationQueueIntegrationTest {

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
    private ReservationQueueWorker reservationQueueWorker;

    @Autowired
    private ReservationQueueClaimService reservationQueueClaimService;

    @Autowired
    private ReservationQueueRecoveryService reservationQueueRecoveryService;

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
    private PurchaseCommandFacade purchaseCommandFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("limited-ticketing.queue.worker.initial-delay", () -> "600000");
        registry.add("limited-ticketing.queue.worker.recovery-initial-delay", () -> "600000");
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
        assertThat(firstEntry.status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(firstEntry.position()).isEqualTo(1);
        assertThat(secondEntry.position()).isEqualTo(2);

        reservationQueueWorker.dispatchWaitingEntries();

        QueueEntry completedFirstEntry = awaitCompletion(firstEntry.id(), fixture.firstMemberId());
        QueueEntry completedSecondEntry = awaitCompletion(secondEntry.id(), fixture.secondMemberId());

        assertThat(List.of(completedFirstEntry.status(), completedSecondEntry.status()))
            .containsExactlyInAnyOrder(ReservationQueueStatus.RESERVED, ReservationQueueStatus.SOLD_OUT);
        assertThat(purchaseRepository.findById(
            completedFirstEntry.status() == ReservationQueueStatus.RESERVED
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
        assertThat(find(entry.id(), fixture.firstMemberId()).status()).isEqualTo(ReservationQueueStatus.WAITING);
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

        reservationQueueWorker.dispatchWaitingEntries();

        QueueEntry completed = awaitCompletion(entry.id(), fixture.firstMemberId());
        assertThat(completed.status()).isEqualTo(ReservationQueueStatus.FAILED);
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
        reservationQueueClaimService.claim(entry.id()).orElseThrow();
        String waitingKey = "waiting:sale:" + fixture.saleProductId();
        String processingKey = "processing:sale:" + fixture.saleProductId();
        stringRedisTemplate.opsForZSet().remove(waitingKey, entry.id().toString());
        stringRedisTemplate.opsForZSet().add(processingKey, entry.id().toString(), 0);
        PurchaseReservation committedPurchase = purchaseCommandFacade.reserve(
            new ReservePurchaseInput(fixture.firstMemberId(), fixture.saleProductId(), requestKey)
        );

        // Simulate a worker stopping after the purchase commits but before the waiting request completes.
        reservationQueueRecoveryService.recover(0);
        reservationQueueWorker.dispatchWaitingEntries();

        QueueEntry recoveredEntry = awaitCompletion(entry.id(), fixture.firstMemberId());
        assertThat(recoveredEntry.status()).isEqualTo(ReservationQueueStatus.RESERVED);
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

        reservationQueueClaimService.claim(entry.id()).orElseThrow();
        stringRedisTemplate.opsForZSet().remove(waitingKey, entry.id().toString());
        stringRedisTemplate.opsForZSet().add(processingKey, entry.id().toString(), 0);

        reservationQueueRecoveryService.recover(0);

        QueueEntry recoveredEntry = find(entry.id(), fixture.firstMemberId());
        assertThat(recoveredEntry.status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(stringRedisTemplate.opsForZSet().rank(waitingKey, entry.id().toString())).isZero();
        assertThat(stringRedisTemplate.opsForZSet().rank(processingKey, entry.id().toString())).isNull();
    }

    @Test
    @DisplayName("Redis에서 가져온 요청이 DB에 없으면 처리 대상에서 제외한다")
    void returnsEmptyWhenClaimedRedisEntryDoesNotExistInDatabase() {
        assertThat(reservationQueueClaimService.claim(UUID.randomUUID())).isEmpty();
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

        reservationQueueRecoveryService.recover(30);

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

        reservationQueueRecoveryService.recover(30);

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

        reservationQueueRecoveryService.recover(30);

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
        reservationQueueWorker.dispatchWaitingEntries();
        awaitCompletion(entry.id(), fixture.firstMemberId());

        SaleProduct saleProduct = saleProductRepository.findById(fixture.saleProductId()).orElseThrow();
        saleProduct.close();
        saleProductRepository.save(saleProduct);

        reservationQueueRecoveryService.recover(30);

        assertThat(stringRedisTemplate.hasKey("waiting:sale:" + fixture.saleProductId())).isFalse();
        assertThat(stringRedisTemplate.hasKey("processing:sale:" + fixture.saleProductId())).isFalse();
        assertThat(stringRedisTemplate.hasKey("sequence:sale:" + fixture.saleProductId())).isFalse();
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
            ReservationQueueStatus.valueOf(data.path("status").asText()),
            data.path("position").isNull() ? null : data.path("position").asLong(),
            data.path("purchaseId").isNull() ? null : data.path("purchaseId").asLong()
        );
    }

    private QueueEntry awaitCompletion(UUID queueEntryId, Long memberId) throws Exception {
        for (int attempts = 0; attempts < 50; attempts++) {
            QueueEntry entry = find(queueEntryId, memberId);
            if (entry.status() != ReservationQueueStatus.WAITING && entry.status() != ReservationQueueStatus.PROCESSING) {
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
            ReservationQueueStatus.valueOf(data.path("status").asText()),
            data.path("position").isNull() ? null : data.path("position").asLong(),
            data.path("purchaseId").isNull() ? null : data.path("purchaseId").asLong()
        );
    }

    private String issuePurchaseKey(Long memberId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/idempotency-keys")
                .header("X-Member-Id", memberId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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

    private record QueueEntry(UUID id, ReservationQueueStatus status, Long position, Long purchaseId) {
    }
}
