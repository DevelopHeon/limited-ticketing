package com.example.limitedticketing.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
@ActiveProfiles("test")
class PaymentLifecycleIntegrationTest {

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
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Test
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
                .header("X-Member-Id", fixture.memberId()))
            .andExpect(status().isCreated());
    }

    private long reserve(Fixture fixture) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId()))
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

    private long responseFieldAsLong(MvcResult result, String fieldName) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path(fieldName).asLong();
    }

    private Fixture createFixture(long inventoryQuantity) {
        Member member = memberRepository.save(Member.register("member-" + System.nanoTime(), "테스트 회원"));
        Product product = productRepository.save(Product.create("치킨 할인 쿠폰", "1만원 할인"));
        LocalDateTime now = LocalDateTime.now();
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
