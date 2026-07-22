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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseConcurrencyIntegrationTest {

    private static final int REQUEST_COUNT = 100;
    private static final int INITIAL_INVENTORY_QUANTITY = 10;

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

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
    void reservesOnlyAvailableInventoryWhenOneHundredMembersRequestConcurrently() throws Exception {
        Fixture fixture = createFixture(INITIAL_INVENTORY_QUANTITY);
        List<Long> memberIds = createMembers(REQUEST_COUNT);

        List<Integer> statusCodes = reserveConcurrently(
            fixture.saleProductId(),
            memberIds,
            memberId -> "purchase-request-" + memberId
        );

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        long pendingPurchaseCount = countPurchasesByStatus(fixture.saleProductId(), PurchaseStatus.PENDING_PAYMENT);
        long createdCount = statusCodes.stream()
            .filter(status -> status == HttpStatus.CREATED.value())
            .count();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(statusCodes).allMatch(status -> status < HttpStatus.INTERNAL_SERVER_ERROR.value());
            softly.assertThat(createdCount).isEqualTo(INITIAL_INVENTORY_QUANTITY);
            softly.assertThat(pendingPurchaseCount).isEqualTo(INITIAL_INVENTORY_QUANTITY);
            softly.assertThat(inventory.getAvailableQuantity()).isZero();
            softly.assertThat(inventory.getReservedQuantity()).isEqualTo(INITIAL_INVENTORY_QUANTITY);
            softly.assertThat(inventory.getSoldQuantity()).isZero();
            softly.assertThat(inventory.getAvailableQuantity()).isGreaterThanOrEqualTo(0);
            softly.assertThat(inventory.getReservedQuantity()).isGreaterThanOrEqualTo(0);
            softly.assertThat(inventory.getSoldQuantity()).isGreaterThanOrEqualTo(0);
            softly.assertThat(
                inventory.getAvailableQuantity() + inventory.getReservedQuantity() + inventory.getSoldQuantity()
            ).isEqualTo(INITIAL_INVENTORY_QUANTITY);
        });
    }

    @Test
    void returnsSameReservationWhenSameMemberRetriesWithSameRequestKeyConcurrently() throws Exception {
        Fixture fixture = createFixture(INITIAL_INVENTORY_QUANTITY);
        Long memberId = createMembers(1).get(0);
        List<Long> memberIds = java.util.Collections.nCopies(REQUEST_COUNT, memberId);

        List<Integer> statusCodes = reserveConcurrently(
            fixture.saleProductId(),
            memberIds,
            ignored -> "purchase-request-idempotent"
        );

        Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
        long activePurchaseCount = countActivePurchases(memberId, fixture.saleProductId());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(statusCodes).allMatch(status -> status < HttpStatus.INTERNAL_SERVER_ERROR.value());
            softly.assertThat(statusCodes).allMatch(status -> status == HttpStatus.CREATED.value());
            softly.assertThat(activePurchaseCount).isEqualTo(1);
            softly.assertThat(inventory.getAvailableQuantity()).isEqualTo(INITIAL_INVENTORY_QUANTITY - 1);
            softly.assertThat(inventory.getReservedQuantity()).isEqualTo(1);
            softly.assertThat(inventory.getSoldQuantity()).isZero();
        });
    }

    private List<Integer> reserveConcurrently(
        Long saleProductId,
        List<Long> memberIds,
        java.util.function.Function<Long, String> purchaseRequestKeyProvider
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
        CountDownLatch ready = new CountDownLatch(memberIds.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Long memberId : memberIds) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent reservation start timed out");
                    }

                    return mockMvc.perform(post("/api/sale-products/{saleProductId}/purchases", saleProductId)
                            .header("X-Member-Id", memberId)
                            .header("Idempotency-Key", purchaseRequestKeyProvider.apply(memberId)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                }));
            }

            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent reservation requests were not ready");
            }
            start.countDown();

            List<Integer> statusCodes = new ArrayList<>();
            for (Future<Integer> result : results) {
                statusCodes.add(result.get(30, TimeUnit.SECONDS));
            }
            return statusCodes;
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
}
