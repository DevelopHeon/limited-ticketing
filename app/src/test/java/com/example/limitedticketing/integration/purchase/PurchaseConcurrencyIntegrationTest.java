package com.example.limitedticketing.integration.purchase;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchase.domain.model.PurchaseStatus;
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
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.InstanceOfAssertFactories.LONG;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "baseline"})
@DisplayName("구매 예약의 동시성과 멱등성 통합 검증")
class PurchaseConcurrencyIntegrationTest {

    private static final int REQUEST_COUNT = 100;
    private static final int INITIAL_INVENTORY_QUANTITY = 10;

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

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Test
    @DisplayName("회원 백 명이 동시에 구매해도 가용 재고 수만큼만 예약한다")
    void reservesOnlyAvailableInventoryWhenOneHundredMembersRequestConcurrently() throws Exception {
        Fixture fixture = createFixture(INITIAL_INVENTORY_QUANTITY);
        List<Long> memberIds = createMembers(REQUEST_COUNT);

        List<ReservationResponse> responses = reserveConcurrently(
            fixture.saleProductId(),
            memberIds,
            memberId -> "purchase-request-" + memberId
        );
        List<Integer> statusCodes = responses.stream().map(ReservationResponse::status).toList();

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        long pendingPurchaseCount = countPurchasesByStatus(fixture.saleProductId(), PurchaseStatus.PENDING_PAYMENT);
        long createdCount = statusCodes.stream()
            .filter(status -> status == HttpStatus.CREATED.value())
            .count();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(statusCodes).containsOnly(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
            softly.assertThat(createdCount).isEqualTo(INITIAL_INVENTORY_QUANTITY);
            softly.assertThat(pendingPurchaseCount).isEqualTo(INITIAL_INVENTORY_QUANTITY);
            softly.assertThat(inventory.getAvailableQuantity()).isZero();
            softly.assertThat(inventory.getReservedQuantity()).isEqualTo(INITIAL_INVENTORY_QUANTITY);
            softly.assertThat(inventory.getSoldQuantity()).isZero();
            softly.assertThat(
                inventory.getAvailableQuantity() + inventory.getReservedQuantity() + inventory.getSoldQuantity()
            ).isEqualTo(INITIAL_INVENTORY_QUANTITY);
        });
    }

    @Test
    @DisplayName("동일 회원과 요청 키의 동시 재시도는 같은 구매 식별자를 반환하고 한 번만 예약한다")
    void returnsSameReservationWhenSameMemberRetriesWithSameRequestKeyConcurrently() throws Exception {
        Fixture fixture = createFixture(INITIAL_INVENTORY_QUANTITY);
        Long memberId = createMembers(1).get(0);
        List<Long> memberIds = java.util.Collections.nCopies(REQUEST_COUNT, memberId);

        List<ReservationResponse> responses = reserveConcurrently(
            fixture.saleProductId(),
            memberIds,
            ignored -> "purchase-request-idempotent"
        );
        List<Integer> statusCodes = responses.stream().map(ReservationResponse::status).toList();

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        long activePurchaseCount = countActivePurchases(memberId, fixture.saleProductId());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(statusCodes).containsOnly(HttpStatus.CREATED.value());
            softly.assertThat(responses.stream().map(ReservationResponse::purchaseId).distinct().toList())
                .singleElement(LONG).isPositive();
            softly.assertThat(activePurchaseCount).isEqualTo(1);
            softly.assertThat(inventory.getAvailableQuantity()).isEqualTo(INITIAL_INVENTORY_QUANTITY - 1);
            softly.assertThat(inventory.getReservedQuantity()).isEqualTo(1);
            softly.assertThat(inventory.getSoldQuantity()).isZero();
        });
    }

    private List<ReservationResponse> reserveConcurrently(
        Long saleProductId,
        List<Long> memberIds,
        java.util.function.Function<Long, String> purchaseRequestKeyProvider
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
        CountDownLatch ready = new CountDownLatch(memberIds.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<ReservationResponse>> results = new ArrayList<>();
            for (Long memberId : memberIds) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent reservation start timed out");
                    }

                    MvcResult result = mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", saleProductId)
                            .header("X-Member-Id", memberId)
                            .header("Idempotency-Key", purchaseRequestKeyProvider.apply(memberId)))
                        .andReturn();
                    long purchaseId = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("purchaseId").asLong();
                    return new ReservationResponse(result.getResponse().getStatus(), purchaseId);
                }));
            }

            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent reservation requests were not ready");
            }
            start.countDown();

            List<ReservationResponse> responses = new ArrayList<>();
            for (Future<ReservationResponse> result : results) {
                responses.add(result.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long countPurchasesByStatus(Long saleProductId, PurchaseStatus status) {
        return jdbcTemplate.queryForObject(
            "select count(*) from purchases where sale_product_id = ? and status = ?",
            Long.class,
            saleProductId,
            status.name()
        );
    }

    private long countActivePurchases(Long memberId, Long saleProductId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from purchases where member_id = ? and sale_product_id = ? and active_purchase_key is not null",
            Long.class,
            memberId,
            saleProductId
        );
    }

    private List<Long> createMembers(int count) {
        List<Long> memberIds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Member member = memberRepository.save(
                Member.register("member-" + System.nanoTime() + "-" + index, "테스트 회원 " + index)
            );
            memberIds.add(member.getId());
        }
        return memberIds;
    }

    private Fixture createFixture(long inventoryQuantity) {
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

        return new Fixture(saleProduct.getId(), inventory.getId());
    }

    private record Fixture(Long saleProductId, Long inventoryId) {
    }

    private record ReservationResponse(int status, long purchaseId) {
    }
}
