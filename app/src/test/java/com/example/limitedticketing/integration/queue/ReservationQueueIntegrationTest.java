package com.example.limitedticketing.integration.queue;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.example.limitedticketing.queue.domain.model.ReservationQueueStatus;
import com.example.limitedticketing.queue.application.command.service.ReservationQueueClaimService;
import com.example.limitedticketing.queue.application.command.service.ReservationQueueRecoveryService;
import com.example.limitedticketing.queue.infrastructure.scheduler.ReservationQueueWorker;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        assertThat(stringRedisTemplate.opsForZSet().zCard("processing:sale:" + fixture.saleProductId())).isZero();
    }

    @Test
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
    void returnsEmptyWhenClaimedRedisEntryDoesNotExistInDatabase() {
        assertThat(reservationQueueClaimService.claim(UUID.randomUUID())).isEmpty();
    }

    @Test
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
            MvcResult result = mockMvc.perform(get("/api/queue-entries/{queueEntryId}", queueEntryId)
                    .header("X-Member-Id", memberId))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            ReservationQueueStatus status = ReservationQueueStatus.valueOf(data.path("status").asText());
            if (status == ReservationQueueStatus.RESERVED || status == ReservationQueueStatus.SOLD_OUT) {
                return new QueueEntry(
                    queueEntryId,
                    status,
                    data.path("position").isNull() ? null : data.path("position").asLong(),
                    data.path("purchaseId").isNull() ? null : data.path("purchaseId").asLong()
                );
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
