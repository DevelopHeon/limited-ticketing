package com.example.limitedticketing.integration.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
import com.example.limitedticketing.purchase.domain.event.PurchaseReserved;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.web.servlet.MockMvc;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PurchaseReservationIntegrationTest.EventTestConfiguration.class)
class PurchaseReservationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

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

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @BeforeEach
    void clearRecordedEvents() {
        purchaseReservedEventRecorder.clear();
    }

    @Test
    void reservesInventoryAndCreatesPurchaseForAuthenticatedMember() throws Exception {
        Fixture fixture = createFixture(1);

        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", fixture.saleProductId())
                .header("X-Member-Id", fixture.memberId())
                .header("Idempotency-Key", "purchase-request-1"))
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
    void rejectsRequestWithoutMemberIdentity() throws Exception {
        mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", 1L))
            .andExpect(status().isUnauthorized());
    }

    @Test
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
