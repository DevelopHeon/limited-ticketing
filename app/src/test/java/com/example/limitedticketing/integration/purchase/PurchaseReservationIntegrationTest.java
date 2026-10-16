package com.example.limitedticketing.integration.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.limitedticketing.integration.support.FixedClockConfiguration;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchase.application.service.PurchaseExpirationService;
import com.example.limitedticketing.purchase.domain.event.PurchaseReserved;
import com.example.limitedticketing.purchase.domain.model.Purchase;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "baseline"})
@Import({PurchaseReservationIntegrationTest.EventTestConfiguration.class, FixedClockConfiguration.class})
@DisplayName("구매 예약과 취소 및 만료 통합 검증")
class PurchaseReservationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleProductRepository saleProductRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PurchaseReservedEventRecorder purchaseReservedEventRecorder;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PurchaseExpirationService purchaseExpirationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", postgresql::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", postgresql::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", postgresql::getPassword);
    }

    @BeforeEach
    void clearRecordedEvents() {
        purchaseReservedEventRecorder.clear();
    }

    @Test
    @DisplayName("인증된 회원의 구매 예약은 재고 한 개와 커밋 후 예약 이벤트를 생성한다")
    void reservesInventoryAndCreatesPurchaseForAuthenticatedMember() throws Exception {
        Fixture fixture = createFixture(1);
        String purchaseRequestKey = issuePurchaseKey(fixture.memberId());

        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", purchaseRequestKey))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.purchaseId").isNumber())
            .andExpect(jsonPath("$.data.status").value(PurchaseStatus.PENDING_PAYMENT.name()));

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(purchaseReservedEventRecorder.events())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.memberId()).isEqualTo(fixture.memberId());
                assertThat(event.saleProductId()).isEqualTo(fixture.saleProductId());
            });
    }

    @Test
    @DisplayName("인증된 회원에게 서로 다른 구매 멱등성 키를 발급한다")
    void issuesUniquePurchaseKeysForAuthenticatedMember() throws Exception {
        Fixture fixture = createFixture(1);

        String firstKey = issuePurchaseKey(fixture.memberId());
        String secondKey = issuePurchaseKey(fixture.memberId());

        assertThat(firstKey)
            .matches("purchase_" + fixture.memberId() + "_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(secondKey).isNotEqualTo(firstKey);
    }

    @Test
    @DisplayName("회원 인증이 없는 구매 요청을 거부한다")
    void rejectsRequestWithoutMemberIdentity() throws Exception {
        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 활성 구매가 있으면 추가 구매와 재고 예약을 거부하고 이벤트를 반복하지 않는다")
    void rollsBackInventoryReservationWhenMemberAlreadyHasAnActivePurchase() throws Exception {
        Fixture fixture = createFixture(2);

        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "purchase-request-1"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "purchase-request-2"))
            .andExpect(status().isConflict());

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(purchaseReservedEventRecorder.events()).hasSize(1);
    }

    @Test
    @DisplayName("품절 요청은 구매 행과 재고 예약 및 예약 이벤트를 남기지 않는다")
    void soldOutRequestLeavesNoPurchaseOrReservationEvent() throws Exception {
        Fixture fixture = createFixture(0);

        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "sold-out-purchase"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));

        assertThat(purchaseRepository.findByMemberIdAndPurchaseRequestKey(fixture.memberId(), "sold-out-purchase"))
            .isEmpty();
        assertThat(purchaseReservedEventRecorder.events()).isEmpty();
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("만료된 예약만 한 번 반환하고 새 요청 키의 재구매를 허용한다")
    void expirationReleasesOnlyDueReservationsOnceAndAllowsAnotherPurchase() throws Exception {
        Fixture dueFixture = createFixture(1);
        Fixture futureFixture = createFixture(1);
        long duePurchaseId = reserve(dueFixture);
        long futurePurchaseId = reserve(futureFixture);
        jdbcTemplate.update("update purchases set expires_at = ? where id = ?",
            LocalDateTime.now(clock), duePurchaseId);

        assertThat(purchaseExpirationService.expireDuePurchases()).isEqualTo(1);
        assertThat(purchaseExpirationService.expireDuePurchases()).isZero();

        assertThat(purchaseRepository.findById(duePurchaseId).orElseThrow().getStatus()).isEqualTo(PurchaseStatus.EXPIRED);
        assertThat(purchaseRepository.findById(futurePurchaseId).orElseThrow().getStatus())
            .isEqualTo(PurchaseStatus.PENDING_PAYMENT);
        Inventory releasedInventory = inventoryRepository.findById(dueFixture.inventoryId()).orElseThrow();
        assertThat(releasedInventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(releasedInventory.getReservedQuantity()).isZero();
        assertThat(inventoryRepository.findById(futureFixture.inventoryId()).orElseThrow().getReservedQuantity())
            .isEqualTo(1);

        long newPurchaseId = reserve(dueFixture);
        assertThat(newPurchaseId).isNotEqualTo(duePurchaseId);
        assertThat(inventoryRepository.findById(dueFixture.inventoryId()).orElseThrow().getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 회원은 구매를 취소하거나 예약 재고를 반환할 수 없다")
    void anotherMemberCannotCancelOrReleaseTheReservation() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);
        Member anotherMember = memberRepository.save(Member.register("cancel-other-member", "다른 회원"));

        mockMvc.perform(post("/api/purchases/{purchaseId}/cancel", purchaseId)
                .header("X-Member-Id", anotherMember.getId()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PURCHASE_OWNERSHIP_VIOLATION"));

        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getStatus())
            .isEqualTo(PurchaseStatus.PENDING_PAYMENT);
        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
    }

    private long reserve(Fixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "purchase-" + java.util.UUID.randomUUID()))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("purchaseId").asLong();
    }

    @Test
    @DisplayName("구매 취소는 예약 재고를 반환하고 새 구매 예약을 허용한다")
    void cancellingPurchaseReleasesReservationAndAllowsNewReservation() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);

        mockMvc.perform(post("/api/purchases/{purchaseId}/cancel", purchaseId)
                .header("X-Member-Id", fixture.memberId()))
            .andExpect(status().isNoContent());

        Purchase cancelledPurchase = purchaseRepository.findById(purchaseId).orElseThrow();
        Inventory releasedInventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(cancelledPurchase.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(releasedInventory.getAvailableQuantity()).isEqualTo(1);
        assertThat(releasedInventory.getReservedQuantity()).isZero();

        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "purchase-request-after-cancel"))
            .andExpect(status().isCreated());
    }

    private Fixture createFixture(long inventoryQuantity) {
        Member member = memberRepository.save(Member.register("member-" + System.nanoTime(), "테스트 회원"));
        Product product = productRepository.save(Product.create("치킨 할인 쿠폰", "1만원 할인"));
        LocalDateTime now = LocalDateTime.now(clock);
        SaleProduct saleProduct = SaleProduct.schedule(
            product.getId(),
            Money.of(BigDecimal.valueOf(10_000)),
            SalePeriod.of(now.minusMinutes(1), now.plusHours(1))
        );
        saleProduct.open(now);
        saleProduct = saleProductRepository.save(saleProduct);
        Inventory inventory = inventoryRepository.save(Inventory.initialize(saleProduct.getId(), inventoryQuantity));

        return new Fixture(member.getId(), saleProduct.getId(), inventory.getId());
    }

    private String issuePurchaseKey(Long memberId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/idempotency-keys")
                .header("X-Member-Id", memberId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"operation\":\"PURCHASE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.operation").value("PURCHASE"))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("idempotencyKey").asText();
    }

    private record Fixture(Long memberId, Long saleProductId, Long inventoryId) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventTestConfiguration {

        @Bean
        PurchaseReservedEventRecorder purchaseReservedEventRecorder() {
            return new PurchaseReservedEventRecorder();
        }
    }

    static class PurchaseReservedEventRecorder {

        private final List<PurchaseReserved> events = new ArrayList<>();

        @EventListener
        public void record(PurchaseReserved event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        List<PurchaseReserved> events() {
            return List.copyOf(events);
        }
    }
}
