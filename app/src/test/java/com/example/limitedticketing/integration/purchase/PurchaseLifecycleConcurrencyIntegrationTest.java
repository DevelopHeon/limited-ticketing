package com.example.limitedticketing.integration.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("구매 및 결제 상태 전이의 동시성 통합 검증")
class PurchaseLifecycleConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
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

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @CsvSource({"approve,approve", "cancel,cancel", "approve,cancel", "approve,fail", "fail,fail"})
    @DisplayName("구매와 결제의 동시 상태 전이는 한 번만 성공하고 다른 회원의 예약 재고를 보호한다")
    void concurrentTransitionsOfOnePurchaseDoNotConsumeAnotherMembersReservation(String firstAction, String secondAction)
        throws Exception {
        Fixture fixture = createTwoReservedPurchases();
        List<String> actions = List.of(firstAction, secondAction);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new java.util.ArrayList<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                // Hold the affected row so both requests overlap before either transition can commit.
                if (actions.contains("fail")) {
                    jdbcTemplate.queryForObject("select id from payments where id = ? for update",
                        Long.class, fixture.paymentId());
                } else {
                    jdbcTemplate.queryForObject("select id from inventories where id = ? for update",
                        Long.class, fixture.inventoryId());
                }
                for (String action : actions) {
                    String path = action.equals("cancel")
                        ? "/api/purchases/" + fixture.purchaseId() + "/cancel"
                        : "/api/payments/" + fixture.paymentId() + "/" + action;
                    results.add(executor.submit(() -> mockMvc.perform(post(path)
                            .header("X-Member-Id", fixture.memberId()))
                        .andReturn().getResponse().getStatus()));
                }
                await().atMost(Duration.ofSeconds(10)).until(() ->
                    jdbcTemplate.queryForObject("select count(*) from pg_locks where not granted", Long.class) >= 2
                );
            });
            List<Integer> statuses = List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(1);
            int winnerIndex = statuses.get(0) == 409 ? 1 : 0;
            String winningAction = actions.get(winnerIndex);
            assertThat(statuses.get(winnerIndex)).isEqualTo(winningAction.equals("cancel") ? 204 : 200);

            Inventory inventory = inventoryRepository.findById(fixture.inventoryId()).orElseThrow();
            assertThat(inventory.getReservedQuantity()).isEqualTo(winningAction.equals("fail") ? 2 : 1);
            assertThat(inventory.getAvailableQuantity()).isEqualTo(winningAction.equals("cancel") ? 1 : 0);
            assertThat(inventory.getSoldQuantity()).isEqualTo(winningAction.equals("approve") ? 1 : 0);
            assertThat(purchaseRepository.findById(fixture.otherPurchaseId()).orElseThrow().getStatus())
                .isEqualTo(PurchaseStatus.PENDING_PAYMENT);
            PurchaseStatus expectedStatus = switch (winningAction) {
                case "approve" -> PurchaseStatus.PAID;
                case "cancel" -> PurchaseStatus.CANCELLED;
                default -> PurchaseStatus.PENDING_PAYMENT;
            };
            assertThat(purchaseRepository.findById(fixture.purchaseId()).orElseThrow().getStatus()).isEqualTo(expectedStatus);
            PaymentStatus expectedPaymentStatus = switch (winningAction) {
                case "approve" -> PaymentStatus.APPROVED;
                case "fail" -> PaymentStatus.FAILED;
                default -> PaymentStatus.READY;
            };
            assertThat(paymentRepository.findById(fixture.paymentId()).orElseThrow().getStatus())
                .isEqualTo(expectedPaymentStatus);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createTwoReservedPurchases() {
        Member member = memberRepository.save(Member.register("member-" + UUID.randomUUID(), "회원"));
        Member otherMember = memberRepository.save(Member.register("member-" + UUID.randomUUID(), "다른 회원"));
        Product product = productRepository.save(Product.create("동시 전이 상품", "재고 보호"));
        LocalDateTime now = LocalDateTime.now();
        Money price = Money.of(BigDecimal.valueOf(10_000));
        SaleProduct sale = SaleProduct.createDraft(product.getId(), price, SalePeriod.of(now.minusMinutes(1), now.plusHours(1)));
        sale.open(now);
        SaleProduct savedSale = saleProductRepository.save(sale);
        Inventory inventory = Inventory.initialize(savedSale.getId(), 2);
        inventory.reserve(2);
        Inventory savedInventory = inventoryRepository.save(inventory);
        Purchase purchase = purchaseRepository.save(Purchase.reserve(
            member.getId(), savedSale.getId(), "purchase-" + UUID.randomUUID(), price, now.plusMinutes(5)));
        Purchase otherPurchase = purchaseRepository.save(Purchase.reserve(
            otherMember.getId(), savedSale.getId(), "purchase-" + UUID.randomUUID(), price, now.plusMinutes(5)));
        Payment payment = paymentRepository.save(Payment.request(purchase.getId(), "payment-" + UUID.randomUUID(), price));
        return new Fixture(member.getId(), purchase.getId(), otherPurchase.getId(), payment.getId(), savedInventory.getId());
    }

    private record Fixture(Long memberId, Long purchaseId, Long otherPurchaseId, Long paymentId, Long inventoryId) {
    }
}
