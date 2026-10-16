package com.example.limitedticketing.integration.purchasewaiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
    "limited-ticketing.queue.admission.max-outstanding=2", "spring.data.redis.timeout=200ms",
    "limited-ticketing.queue.worker.initial-delay=600000", "limited-ticketing.queue.worker.recovery-initial-delay=600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("구매 대기 입구 수용량과 요청 제한 및 장애 응답 검증")
class PurchaseWaitingProtectionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc http;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private SaleProductRepository saleProductRepository;
    @Autowired
    private InventoryRepository inventoryRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", postgresql::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", postgresql::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", postgresql::getPassword);
        dynamicPropertyRegistry.add("spring.data.redis.host", redis::getHost);
        dynamicPropertyRegistry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Test
    @DisplayName("기본 실행에서는 직접 예약 HTTP를 제공하지 않고 재고를 변경하지 않는다")
    void directReservationIsUnavailableWithoutTheBaselineProfile() throws Exception {
        Fixture fixture = fixture();
        http.perform(post("/api/sale-products/{id}/purchases", fixture.saleId())
                .header("X-Member-Id", fixture.memberIds().get(0))
                .header("Idempotency-Key", key(fixture.memberIds().get(0))))
            .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from purchases where sale_product_id = ?", Long.class,
            fixture.saleId())).isZero();
        assertThat(jdbcTemplate.queryForObject("select available_quantity from inventories where sale_product_id = ?", Long.class,
            fixture.saleId())).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 키의 멱등 재전송은 허용하고 1초 이내 새 키의 진입만 제한한다")
    void idempotentRetryDoesNotConsumeTheNewEntryRateLimit() throws Exception {
        Fixture fixture = fixture();
        Long member = fixture.memberIds().get(0);
        String firstKey = key(member);
        MvcResult first = http.perform(enqueue(fixture, member, firstKey)).andExpect(status().isAccepted()).andReturn();
        MvcResult retried = http.perform(enqueue(fixture, member, firstKey)).andExpect(status().isAccepted()).andReturn();
        assertThat(mapper.readTree(retried.getResponse().getContentAsString()).path("data").path("queueEntryId"))
            .isEqualTo(mapper.readTree(first.getResponse().getContentAsString()).path("data").path("queueEntryId"));
        http.perform(enqueue(fixture, member, key(member)))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"))
            .andExpect(jsonPath("$.code").value("QUEUE_RATE_LIMITED"));
        assertThat(count(fixture)).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 진입에서도 상품별 수용량만 수락하고 초과 요청의 원장은 만들지 않는다")
    void concurrentAdmissionCannotExceedCapacity() throws Exception {
        Fixture fixture = fixture();
        List<String> keys = new ArrayList<>();
        for (Long member : fixture.memberIds()) {
            keys.add(key(member));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        List<Future<Integer>> requests = new ArrayList<>();
        try {
            for (int index = 0; index < 3; index++) {
                int memberIndex = index;
                requests.add(executorService.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Admission start timed out");
                    }
                    MvcResult result = http.perform(enqueue(fixture, fixture.memberIds().get(memberIndex), keys.get(memberIndex)))
                        .andReturn();
                    if (result.getResponse().getStatus() == 429) {
                        assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("1");
                        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("code").asText())
                            .isEqualTo("QUEUE_CAPACITY_EXCEEDED");
                    }
                    return result.getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> request : requests) {
                statuses.add(request.get(10, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(202, 202, 429);
            assertThat(count(fixture)).isEqualTo(2);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Redis 장애의 신규 진입은 503으로 거부하고 수락한 기존 원장은 순번 없이 조회한다")
    void redisFailureRejectsNewAdmissionButKeepsAcceptedRequestsReadable() throws Exception {
        Fixture fixture = fixture();
        Long firstMember = fixture.memberIds().get(0);
        MvcResult accepted = http.perform(enqueue(fixture, firstMember, key(firstMember)))
            .andExpect(status().isAccepted()).andReturn();
        String id = mapper.readTree(accepted.getResponse().getContentAsString()).path("data").path("queueEntryId").asText();
        Long secondMember = fixture.memberIds().get(1);
        String secondKey = key(secondMember);
        redis.execInContainer("redis-cli", "CLIENT", "PAUSE", "2000", "ALL");
        try {
            http.perform(enqueue(fixture, secondMember, secondKey))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("QUEUE_UNAVAILABLE"));
            assertThat(count(fixture)).isEqualTo(1);
            http.perform(get("/api/queue-entries/{id}", id).header("X-Member-Id", firstMember))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").isEmpty());
        } finally {
            redis.execInContainer("redis-cli", "CLIENT", "UNPAUSE");
        }
    }

    @Test
    @DisplayName("동일 키의 동시 재전송은 속도 제한 없이 하나의 대기 원장을 반환한다")
    void concurrentRetriesReturnOneAcceptedEntry() throws Exception {
        Fixture fixture = fixture();
        Long member = fixture.memberIds().get(0);
        String key = key(member);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int index = 0; index < 8; index++) {
                responses.add(executorService.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Retry start timed out");
                    }
                    return http.perform(enqueue(fixture, member, key)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> response : responses) {
                assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(202);
            }
            assertThat(count(fixture)).isEqualTo(1);
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("Redis 장애 중에도 수락한 동일 키 재전송은 기존 원장을 202로 반환한다")
    void acceptedRetrySurvivesRedisOutage() throws Exception {
        Fixture fixture = fixture();
        Long member = fixture.memberIds().get(0);
        String requestKey = key(member);
        MvcResult accepted = http.perform(enqueue(fixture, member, requestKey)).andExpect(status().isAccepted()).andReturn();
        String id = mapper.readTree(accepted.getResponse().getContentAsString()).path("data").path("queueEntryId").asText();
        redis.execInContainer("redis-cli", "CLIENT", "PAUSE", "2000", "ALL");
        try {
            http.perform(enqueue(fixture, member, requestKey)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.queueEntryId").value(id))
                .andExpect(jsonPath("$.data.position").isEmpty());
            assertThat(count(fixture)).isEqualTo(1);
        } finally {
            redis.execInContainer("redis-cli", "CLIENT", "UNPAUSE");
        }
    }

    private long count(Fixture fixture) {
        return jdbcTemplate.queryForObject("select count(*) from reservation_queue_entries where sale_product_id = ?",
            Long.class, fixture.saleId());
    }

    private MockHttpServletRequestBuilder enqueue(Fixture fixture, Long member, String key) {
        return post("/api/sale-products/{id}/queue-entries", fixture.saleId())
            .header("X-Member-Id", member).header("Idempotency-Key", key);
    }

    private String key(Long member) throws Exception {
        MvcResult result = http.perform(post("/api/idempotency-keys").header("X-Member-Id", member)
                .contentType("application/json").content("{\"operation\":\"PURCHASE\"}"))
            .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("idempotencyKey").asText();
    }

    private Fixture fixture() {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            ids.add(memberRepository.save(Member.register("protection-" + System.nanoTime(), "입구 정책 회원")).getId());
        }
        Product product = productRepository.save(Product.create("입구 정책 상품", "제한 검증"));
        LocalDateTime now = LocalDateTime.now();
        SaleProduct sale = SaleProduct.createDraft(product.getId(), Money.of(BigDecimal.valueOf(10000)),
            SalePeriod.of(now.minusMinutes(1), now.plusHours(1)));
        sale.open(now);
        sale = saleProductRepository.save(sale);
        inventoryRepository.save(Inventory.initialize(sale.getId(), 2));
        return new Fixture(sale.getId(), ids);
    }

    private record Fixture(Long saleId, List<Long> memberIds) {
    }
}
