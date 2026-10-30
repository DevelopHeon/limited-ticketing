package com.example.limitedticketing.integration.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.limitedticketing.sale.application.port.in.SaleProductPurchasePort;
import com.example.limitedticketing.sale.application.port.in.dto.FindPurchasableSaleProductInput;
import com.example.limitedticketing.sale.application.port.out.SaleProductReadCachePort;
import com.example.limitedticketing.sale.application.port.out.dto.CacheFillLease;
import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductDetail;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductPage;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductSort;
import com.example.limitedticketing.sale.application.port.out.dto.SortDirection;
import com.example.limitedticketing.sale.application.service.SaleProductQueryService;
import com.example.limitedticketing.sale.application.usecase.CloseSaleProductUseCase;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductDetailUseCase;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductsUseCase;
import com.example.limitedticketing.sale.application.usecase.IsSaleProductClosedUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.command.CloseSaleProductCommand;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductDetailResult;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductPageResult;
import com.example.limitedticketing.sale.domain.exception.SaleException;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.sale.infrastructure.adapter.out.redis.RedisSaleProductReadCacheAdapter;
import com.example.limitedticketing.sale.infrastructure.adapter.out.redis.SaleProductCacheCodec;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import com.example.limitedticketing.sale.infrastructure.metrics.SaleCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {"limited-ticketing.sale-cache.enabled=true",
    "limited-ticketing.sale-cache.jitter-percent=0", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "api"})
@DisplayName("상품 표시 캐시와 실제 PostgreSQL 및 조회 전용 Redis의 정합성")
class SaleProductReadCacheIntegrationTest {
    private static final String PREFIX = "sale-cache:{catalog}:v1:";
    @Container static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static final GenericContainer<?> queueRedis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container static final GenericContainer<?> readRedis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("spring.data.redis.host", queueRedis::getHost);
        registry.add("spring.data.redis.port", () -> queueRedis.getMappedPort(6379));
        registry.add("limited-ticketing.sale-cache.redis.host", readRedis::getHost);
        registry.add("limited-ticketing.sale-cache.redis.port", () -> readRedis.getMappedPort(6379));
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private SaleProductReadCachePort saleProductReadCachePort;
    @Autowired private FindSaleProductDetailUseCase findSaleProductDetailUseCase;
    @Autowired private FindSaleProductsUseCase findSaleProductsUseCase;
    @Autowired private IsSaleProductClosedUseCase isSaleProductClosedUseCase;
    @Autowired private CloseSaleProductUseCase closeSaleProductUseCase;
    @Autowired private SaleProductPurchasePort saleProductPurchasePort;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleProductRepository saleProductRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired @Qualifier("saleCacheStringRedisTemplate") private StringRedisTemplate saleCacheStringRedisTemplate;
    @Autowired private SaleProductCacheCodec saleProductCacheCodec;
    @Autowired private MeterRegistry meterRegistry;
    @MockitoSpyBean private SaleProductQueryService saleProductQueryService;
    @MockitoSpyBean private RedisSaleProductReadCacheAdapter redisSaleProductReadCacheAdapter;

    @BeforeEach
    void startNewGeneration() {
        saleProductReadCachePort.invalidate();
    }

    @Test
    @DisplayName("상세와 목록 warm hit는 DB SQL 없이 같은 정보를 반환하며 목록 miss는 상품을 일괄 조회한다")
    void warmReadsAvoidSqlAndPageMissBatchesProducts() throws Exception {
        long saleId = fixture();
        for (int i = 0; i < 3; i++) {
            fixture();
        }
        Statistics statistics = statistics();
        String first = mockMvc.perform(get("/api/sale-products/{id}", saleId)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        statistics.clear();
        String second = mockMvc.perform(get("/api/sale-products/{id}", saleId)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(statistics.getPrepareStatementCount()).isZero();

        FindSaleProductsQuery query = new FindSaleProductsQuery(SaleStatus.OPEN, 0, 2, SaleProductSort.ID, SortDirection.ASC);
        statistics.clear();
        SaleProductPageResult page = findSaleProductsUseCase.findPage(query);
        assertThat(page.items()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        statistics.clear();
        assertThat(findSaleProductsUseCase.findPage(query)).isEqualTo(page);
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @DisplayName("가격이 같은 판매의 페이지 조회도 보조 ID 정렬로 항목이 중복되거나 누락되지 않는다")
    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @EnumSource(SortDirection.class)
    void equalPricePaginationHasStableIdOrdering(SortDirection direction) {
        fixture();
        fixture();
        fixture();
        List<Long> ids = new ArrayList<>();
        SaleProductPageResult first = findSaleProductsUseCase.findPage(
            new FindSaleProductsQuery(SaleStatus.OPEN, 0, 2, SaleProductSort.SALE_PRICE, direction));
        for (int page = 0; page * 2L < first.totalCount(); page++) {
            SaleProductPageResult result = page == 0 ? first : findSaleProductsUseCase.findPage(
                new FindSaleProductsQuery(SaleStatus.OPEN, page, 2, SaleProductSort.SALE_PRICE, direction));
            result.items().forEach(item -> ids.add(item.saleProductId()));
        }
        assertThat(ids).doesNotHaveDuplicates().hasSize(Math.toIntExact(first.totalCount()));
        assertThat(ids).isSortedAccordingTo(direction == SortDirection.ASC ? Long::compareTo : (left, right) -> right.compareTo(left));
    }

    @Test
    @DisplayName("존재하지 않는 상세 요청은 성공 캐시를 남기지 않고 매번 404로 응답한다")
    void missingDetailIsNeverCachedAsASuccess() throws Exception {
        FindSaleProductQuery query = new FindSaleProductQuery(Long.MAX_VALUE);
        clearInvocations(saleProductQueryService);

        mockMvc.perform(get("/api/sale-products/{id}", query.saleProductId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sale-products/{id}", query.saleProductId())).andExpect(status().isNotFound());

        verify(saleProductQueryService, times(2)).loadDetail(query);
    }

    @Test
    @DisplayName("손상된 generation 문자열도 서버 오류 대신 새 세대로 복원하고 DB 결과를 반환한다")
    void invalidGenerationMetadataDoesNotFailTheRead() {
        long saleId = fixture();
        saleCacheStringRedisTemplate.opsForValue().set(PREFIX + "generation", "broken-generation");

        assertThat(findSaleProductDetailUseCase.findDetail(new FindSaleProductQuery(saleId)).saleProductId()).isEqualTo(saleId);

        assertThatCode(() -> UUID.fromString(saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("generation key가 유실되면 새 세대로 복원하여 남아 있는 과거 캐시를 재사용하지 않는다")
    void generationLossDoesNotReuseOldCacheEntries() {
        long saleId = fixture();
        FindSaleProductQuery query = new FindSaleProductQuery(saleId);
        findSaleProductDetailUseCase.findDetail(query);
        String oldGeneration = saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation");
        assertThat(saleCacheStringRedisTemplate.hasKey(detailKey(UUID.fromString(oldGeneration), saleId))).isTrue();
        saleCacheStringRedisTemplate.delete(PREFIX + "generation");
        clearInvocations(saleProductQueryService);

        findSaleProductDetailUseCase.findDetail(query);

        assertThat(saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation")).isNotEqualTo(oldGeneration);
        verify(saleProductQueryService).loadDetail(query);
    }

    @Test
    @DisplayName("무효화가 누락돼도 판매 변경은 commit되고 기존 표시 TTL 만료 후 새 상태로 복구한다")
    void missedInvalidationRecoversAfterExpirationWithoutFailingTheSale() {
        long saleId = fixture();
        FindSaleProductQuery query = new FindSaleProductQuery(saleId);
        findSaleProductDetailUseCase.findDetail(query);
        UUID generation = UUID.fromString(saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation"));
        doThrow(new RedisConnectionFailureException("무효화 장애"))
            .when(redisSaleProductReadCacheAdapter).invalidate();

        closeSaleProductUseCase.close(new CloseSaleProductCommand(saleId));

        assertThat(isSaleProductClosedUseCase.isClosed(query)).isTrue();
        assertThat(findSaleProductDetailUseCase.findDetail(query).status()).isEqualTo(SaleStatus.OPEN);
        String key = detailKey(generation, saleId);
        saleCacheStringRedisTemplate.expire(key, Duration.ofMillis(1));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(saleCacheStringRedisTemplate.hasKey(key)));
        assertThat(findSaleProductDetailUseCase.findDetail(query).status()).isEqualTo(SaleStatus.CLOSED);
    }

    @Test
    @DisplayName("캐시에 OPEN이 남아 있어도 구매 가능 여부와 내부 종료 판정은 DB의 CLOSED를 따른다")
    void staleDisplayCannotAuthorizeAClosedSale() {
        long saleId = fixture();
        FindSaleProductQuery query = new FindSaleProductQuery(saleId);
        assertThat(findSaleProductDetailUseCase.findDetail(query).status()).isEqualTo(SaleStatus.OPEN);
        jdbcTemplate.update("UPDATE sale_products SET status = 'CLOSED' WHERE id = ?", saleId);

        assertThat(findSaleProductDetailUseCase.findDetail(query).status()).isEqualTo(SaleStatus.OPEN);
        assertThat(isSaleProductClosedUseCase.isClosed(query)).isTrue();
        assertThatThrownBy(() -> saleProductPurchasePort.findPurchasable(
            new FindPurchasableSaleProductInput(saleId, LocalDateTime.now())))
            .isInstanceOf(SaleException.class).extracting("code").isEqualTo("SALE_UNAVAILABLE");
    }

    @Test
    @DisplayName("판매 종료 rollback은 기존 generation을 유지하고 commit은 상세와 목록을 무효화한다")
    void rollbackKeepsGenerationAndCommitInvalidatesAllViews() {
        long saleId = fixture();
        FindSaleProductQuery query = new FindSaleProductQuery(saleId);
        findSaleProductDetailUseCase.findDetail(query);
        FindSaleProductsQuery pageQuery = new FindSaleProductsQuery(SaleStatus.OPEN, 0, 100, SaleProductSort.ID, SortDirection.ASC);
        findSaleProductsUseCase.findPage(pageQuery);
        String generation = saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation");
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            closeSaleProductUseCase.close(new CloseSaleProductCommand(saleId));
            transaction.setRollbackOnly();
        });
        assertThat(saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation")).isEqualTo(generation);
        assertThat(findSaleProductDetailUseCase.findDetail(query).status()).isEqualTo(SaleStatus.OPEN);

        closeSaleProductUseCase.close(new CloseSaleProductCommand(saleId));

        assertThat(saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation")).isNotEqualTo(generation);
        assertThat(findSaleProductDetailUseCase.findDetail(query).status()).isEqualTo(SaleStatus.CLOSED);
        assertThat(findSaleProductsUseCase.findPage(pageQuery).items()).extracting("saleProductId").doesNotContain(saleId);
    }

    @Test
    @DisplayName("변경 전에 읽던 요청의 늦은 저장은 새 generation의 캐시를 채우지 못한다")
    void invalidationRejectsAnOlderGenerationFill() {
        long saleId = fixture();
        CacheFillLease lease = saleProductReadCachePort.findDetail(saleId).lease();
        SaleProductDetailResult detail = saleProductQueryService.loadDetail(new FindSaleProductQuery(saleId));
        saleProductReadCachePort.invalidate();

        saleProductReadCachePort.storeDetail(saleId, lease, detail.toCacheValue());
        saleProductReadCachePort.releaseDetail(saleId, lease);

        CacheLookup<CachedSaleProductDetail> current = saleProductReadCachePort.findDetail(saleId);
        assertThat(current.status()).isEqualTo(CacheLookup.Status.CLAIMED);
        saleProductReadCachePort.releaseDetail(saleId, current.lease());
    }

    @Test
    @DisplayName("만료된 재생성 소유자의 저장과 반환은 새 소유자의 lock을 침범하지 않는다")
    void expiredOwnerCannotPublishOrReleaseTheNewOwner() {
        long saleId = fixture();
        RedisSaleProductReadCacheAdapter shortLeaseAdapter = new RedisSaleProductReadCacheAdapter(
            saleCacheStringRedisTemplate, saleProductCacheCodec, new SaleCacheMetrics(meterRegistry), true, 30000, 10000, 50, 0);
        CacheFillLease old = shortLeaseAdapter.findDetail(saleId).lease();
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(
            saleCacheStringRedisTemplate.hasKey(detailKey(old.generation(), saleId) + ":lock")));
        CacheFillLease current = saleProductReadCachePort.findDetail(saleId).lease();
        SaleProductDetailResult detail = saleProductQueryService.loadDetail(new FindSaleProductQuery(saleId));

        saleProductReadCachePort.storeDetail(saleId, old, detail.toCacheValue());
        saleProductReadCachePort.releaseDetail(saleId, old);
        assertThat(saleProductReadCachePort.findDetail(saleId).status()).isEqualTo(CacheLookup.Status.BUSY);
        saleProductReadCachePort.storeDetail(saleId, current, detail.toCacheValue());
        saleProductReadCachePort.releaseDetail(saleId, current);
        assertThat(saleProductReadCachePort.findDetail(saleId).value()).isEqualTo(detail.toCacheValue());
    }

    @Test
    @DisplayName("만료된 freshness deadline의 결과는 lock이 남아 있어도 캐시에 저장하지 않는다")
    void expiredFreshnessCannotStartANewTtl() {
        long saleId = fixture();
        CacheFillLease lease = saleProductReadCachePort.findDetail(saleId).lease();
        CacheFillLease expired = new CacheFillLease(lease.generation(), lease.token(), 0);

        saleProductReadCachePort.storeDetail(saleId, expired, saleProductQueryService.loadDetail(new FindSaleProductQuery(saleId)).toCacheValue());

        assertThat(saleCacheStringRedisTemplate.hasKey(detailKey(lease.generation(), saleId))).isFalse();
        saleProductReadCachePort.releaseDetail(saleId, lease);
    }

    @Test
    @DisplayName("손상된 캐시와 TTL 만료는 DB로 복원하고 cache hit로 유효 시간을 연장하지 않는다")
    void corruptAndExpiredValuesRecoverFromDatabaseWithoutSlidingTtl() {
        long saleId = fixture();
        FindSaleProductQuery query = new FindSaleProductQuery(saleId);
        SaleProductDetailResult detail = findSaleProductDetailUseCase.findDetail(query);
        UUID generation = UUID.fromString(saleCacheStringRedisTemplate.opsForValue().get(PREFIX + "generation"));
        String key = detailKey(generation, saleId);
        Long before = saleCacheStringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(before).isPositive().isLessThanOrEqualTo(30000);
        assertThat(findSaleProductDetailUseCase.findDetail(query)).isEqualTo(detail);
        assertThat(saleCacheStringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(before);

        saleCacheStringRedisTemplate.opsForValue().set(key, "{broken", Duration.ofSeconds(1));
        assertThat(findSaleProductDetailUseCase.findDetail(query)).isEqualTo(detail);
        assertThat(findSaleProductDetailUseCase.findDetail(query)).isEqualTo(detail);
        saleCacheStringRedisTemplate.expire(key, Duration.ofMillis(1));
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(saleCacheStringRedisTemplate.hasKey(key)));
        clearInvocations(saleProductQueryService);
        assertThat(findSaleProductDetailUseCase.findDetail(query)).isEqualTo(detail);
        verify(saleProductQueryService).loadDetail(query);
    }

    @Test
    @DisplayName("동시 상세 miss는 정상 Lease 안에서 한 번 재생성하고 같은 결과를 공유한다")
    void concurrentMissesShareOneDatabaseLoad() throws Exception {
        long saleId = fixture();
        FindSaleProductQuery query = new FindSaleProductQuery(saleId);
        findSaleProductDetailUseCase.findDetail(query);
        saleProductReadCachePort.invalidate();
        clearInvocations(saleProductQueryService);
        doAnswer(invocation -> {
            Thread.sleep(50);
            return invocation.callRealMethod();
        }).when(saleProductQueryService).loadDetail(query);
        ExecutorService executorService = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SaleProductDetailResult>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executorService.submit(() -> {
                    start.await();
                    return findSaleProductDetailUseCase.findDetail(query);
                }));
            }
            start.countDown();
            for (Future<SaleProductDetailResult> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).saleProductId()).isEqualTo(saleId);
            }
            verify(saleProductQueryService, times(1)).loadDetail(query);
        } finally {
            executorService.shutdownNow();
        }
    }

    @ParameterizedTest(name = "{displayName} [{arguments}]")
    @CsvSource({"OPEN,0,20,ID,ASC", "ALL,1,20,ID,ASC", "ALL,0,10,ID,ASC", "ALL,0,20,SALE_PRICE,ASC", "ALL,0,20,ID,DESC"})
    @DisplayName("목록 필터와 페이지 및 정렬 조건이 다르면 같은 캐시를 공유하지 않는다")
    void pageKeysIncludeAllQueryConditions(String status, int page, int size, SaleProductSort sort, SortDirection direction) {
        FindSaleProductsQuery base = new FindSaleProductsQuery(null, 0, 20, SaleProductSort.ID, SortDirection.ASC);
        CacheFillLease lease = saleProductReadCachePort.findPage(base.toCacheQuery()).lease();
        saleProductReadCachePort.storePage(base.toCacheQuery(), lease, new SaleProductPageResult(List.of(), 0, 20, 0).toCacheValue());
        saleProductReadCachePort.releasePage(base.toCacheQuery(), lease);
        assertThat(saleProductReadCachePort.findPage(base.toCacheQuery()).status()).isEqualTo(CacheLookup.Status.HIT);
        FindSaleProductsQuery different = new FindSaleProductsQuery("ALL".equals(status) ? null : SaleStatus.valueOf(status), page, size, sort, direction);

        CacheLookup<CachedSaleProductPage> lookup = saleProductReadCachePort.findPage(different.toCacheQuery());

        assertThat(lookup.status()).isEqualTo(CacheLookup.Status.CLAIMED);
        saleProductReadCachePort.releasePage(different.toCacheQuery(), lookup.lease());
    }

    @Test
    @DisplayName("조회 Redis가 중단돼도 상세 조회는 DB로 전환하고 대기열 Redis 연결과 데이터는 보존한다")
    void readRedisFailureDoesNotBreakDatabaseReadsOrQueueRedis() throws Exception {
        long saleId = fixture();
        String queueKey = "cache-test:" + UUID.randomUUID();
        stringRedisTemplate.opsForValue().set(queueKey, "queue-data", Duration.ofMinutes(1));
        readRedis.getDockerClient().pauseContainerCmd(readRedis.getContainerId()).exec();
        try {
            assertThat(findSaleProductDetailUseCase.findDetail(new FindSaleProductQuery(saleId)).saleProductId()).isEqualTo(saleId);
            assertThat(stringRedisTemplate.opsForValue().get(queueKey)).isEqualTo("queue-data");
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
            closeSaleProductUseCase.close(new CloseSaleProductCommand(saleId));
            assertThat(isSaleProductClosedUseCase.isClosed(new FindSaleProductQuery(saleId))).isTrue();
        } finally {
            readRedis.getDockerClient().unpauseContainerCmd(readRedis.getContainerId()).exec();
            stringRedisTemplate.delete(queueKey);
        }
    }

    private long fixture() {
        Product product = productRepository.save(Product.create("학습 상품 " + UUID.randomUUID(), "캐시 학습"));
        LocalDateTime now = LocalDateTime.now();
        SaleProduct saleProduct = SaleProduct.createDraft(product.getId(), Money.of(BigDecimal.TEN),
            SalePeriod.of(now.minusHours(1), now.plusHours(1)));
        saleProduct.open(now);
        return saleProductRepository.save(saleProduct).getId();
    }

    private String detailKey(UUID generation, long saleId) {
        return PREFIX + generation + ":detail:" + saleId;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
