package com.example.limitedticketing.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.limitedticketing.integration.support.FixedClockConfiguration;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.payment.domain.model.Payment;
import com.example.limitedticketing.payment.domain.model.PaymentStatus;
import com.example.limitedticketing.payment.domain.repository.PaymentRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
@Import(FixedClockConfiguration.class)
@DisplayName("결제 흐름과 구매 및 재고 정합성 통합 검증")
class PaymentLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private Clock clock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", postgresql::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", postgresql::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Test
    @DisplayName("결제 실패는 예약을 유지하고 새 결제 시도의 승인은 구매와 재고를 확정한다")
    void failedPaymentKeepsReservationAndRetryApprovalCompletesPurchase() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);
        long failedPaymentId = createPayment(fixture, purchaseId, "payment-request-1");

        mockMvc.perform(post("/api/payments/{paymentId}/fail", failedPaymentId)
                .header("X-Member-Id", fixture.memberId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value(PaymentStatus.FAILED.name()));

        Purchase pendingPurchase = purchaseRepository.findById(purchaseId).orElseThrow();
        Inventory reservedInventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(pendingPurchase.getStatus()).isEqualTo(PurchaseStatus.PENDING_PAYMENT);
        assertThat(reservedInventory.getAvailableQuantity()).isZero();
        assertThat(reservedInventory.getReservedQuantity()).isEqualTo(1);

        long approvedPaymentId = createPayment(fixture, purchaseId, "payment-request-2");
        mockMvc.perform(post("/api/payments/{paymentId}/approve", approvedPaymentId)
                .header("X-Member-Id", fixture.memberId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value(PaymentStatus.APPROVED.name()));

        Payment failedPayment = paymentRepository.findById(failedPaymentId).orElseThrow();
        Payment approvedPayment = paymentRepository.findById(approvedPaymentId).orElseThrow();
        Purchase paidPurchase = purchaseRepository.findById(purchaseId).orElseThrow();
        Inventory soldInventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paidPurchase.getStatus()).isEqualTo(PurchaseStatus.PAID);
        assertThat(soldInventory.getAvailableQuantity()).isZero();
        assertThat(soldInventory.getReservedQuantity()).isZero();
        assertThat(soldInventory.getSoldQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 결제 요청 키로 동시에 요청하면 하나의 결제 시도만 생성한다")
    void returnsSamePaymentForConcurrentRequestsWithSamePaymentRequestKey() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);

        List<Long> paymentIds = createPaymentsConcurrently(fixture, purchaseId, "payment-request-idempotent");
        long paymentCount = jdbcTemplate.queryForObject(
            "select count(*) from payments where payment_request_key = ?",
            Long.class,
            "payment-request-idempotent"
        );

        assertThat(paymentIds).hasSize(20).allSatisfy(paymentId -> assertThat(paymentId).isEqualTo(paymentIds.get(0)));
        assertThat(paymentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("예약 만료 직전의 결제 승인은 구매를 완료한다")
    void approvalJustBeforeExpirationCompletesThePurchase() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);
        long paymentId = createPayment(fixture, purchaseId, "payment-before-expiration");
        jdbcTemplate.update("update purchases set expires_at = ? where id = ?",
            LocalDateTime.now(clock).plusSeconds(1), purchaseId);

        mockMvc.perform(post("/api/payments/{paymentId}/approve", paymentId)
                .header("X-Member-Id", fixture.memberId()))
            .andExpect(status().isOk());

        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getStatus()).isEqualTo(PurchaseStatus.PAID);
        assertThat(inventoryRepository.findById(fixture.inventoryId()).orElseThrow().getSoldQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료 시각의 승인과 새 결제 생성은 예약을 변경하지 않고 거부한다")
    void expirationDeadlineRejectsApprovalAndNewPaymentWithoutChangingTheReservation() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);
        long paymentId = createPayment(fixture, purchaseId, "payment-at-expiration");
        jdbcTemplate.update("update purchases set expires_at = ? where id = ?", LocalDateTime.now(clock), purchaseId);

        mockMvc.perform(post("/api/payments/{paymentId}/approve", paymentId)
                .header("X-Member-Id", fixture.memberId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PURCHASE_RESERVATION_EXPIRED"));
        mockMvc.perform(post("/api/purchases/{purchaseId}/payments", purchaseId)
                .header("X-Member-Id", fixture.memberId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentRequestKey\":\"new-payment-at-expiration\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PURCHASE_RESERVATION_EXPIRED"));

        assertThat(paymentRepository.findByPaymentRequestKey("new-payment-at-expiration")).isEmpty();
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
        assertPendingReservation(fixture, purchaseId);
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @ValueSource(strings = {"approve", "fail"})
    @DisplayName("다른 회원의 결제 승인과 실패 처리는 상태를 변경하지 않고 거부한다")
    void anotherMemberCannotProcessThePayment(String action) throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);
        long paymentId = createPayment(fixture, purchaseId, "payment-ownership-" + action);
        Member anotherMember = memberRepository.save(Member.register("other-payment-" + action, "다른 회원"));

        mockMvc.perform(post("/api/payments/{paymentId}/{action}", paymentId, action)
                .header("X-Member-Id", anotherMember.getId()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PAYMENT_OWNERSHIP_VIOLATION"));

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
        assertPendingReservation(fixture, purchaseId);
    }

    @Test
    @DisplayName("다른 회원의 구매에는 결제 시도를 생성할 수 없다")
    void anotherMemberCannotCreatePaymentForThePurchase() throws Exception {
        Fixture fixture = createFixture(1);
        long purchaseId = reserve(fixture);
        Member anotherMember = memberRepository.save(Member.register("other-payment-create", "다른 회원"));

        mockMvc.perform(post("/api/purchases/{purchaseId}/payments", purchaseId)
                .header("X-Member-Id", anotherMember.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentRequestKey\":\"payment-create-forbidden\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PAYMENT_OWNERSHIP_VIOLATION"));

        assertThat(paymentRepository.findByPaymentRequestKey("payment-create-forbidden")).isEmpty();
        assertPendingReservation(fixture, purchaseId);
    }

    private void assertPendingReservation(Fixture fixture, long purchaseId) {
        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getStatus())
            .isEqualTo(PurchaseStatus.PENDING_PAYMENT);
        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory.getSoldQuantity()).isZero();
    }

    private long reserve(Fixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "purchase-request-" + System.nanoTime()))
            .andExpect(status().isCreated())
            .andReturn();

        return responseFieldAsLong(result, "purchaseId");
    }

    private long createPayment(Fixture fixture, long purchaseId, String paymentRequestKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/purchases/{purchaseId}/payments", purchaseId)
                .header("X-Member-Id", fixture.memberId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentRequestKey\":\"" + paymentRequestKey + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value(PaymentStatus.READY.name()))
            .andReturn();

        return responseFieldAsLong(result, "paymentId");
    }

    private List<Long> createPaymentsConcurrently(Fixture fixture, long purchaseId, String paymentRequestKey)
        throws Exception {
        int requestCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                results.add(executorService.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent payment start timed out");
                    }

                    MvcResult result = mockMvc.perform(post("/api/purchases/{purchaseId}/payments", purchaseId)
                            .header("X-Member-Id", fixture.memberId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paymentRequestKey\":\"" + paymentRequestKey + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
                    return responseFieldAsLong(result, "paymentId");
                }));
            }

            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent payment requests were not ready");
            }
            start.countDown();

            List<Long> paymentIds = new ArrayList<>();
            for (Future<Long> result : results) {
                paymentIds.add(result.get(30, TimeUnit.SECONDS));
            }
            return paymentIds;
        } finally {
            start.countDown();
            executorService.shutdownNow();
        }
    }

    private long responseFieldAsLong(MvcResult result, String fieldName) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path(fieldName).asLong();
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

    private record Fixture(Long memberId, Long saleProductId, Long inventoryId) {
    }
}
