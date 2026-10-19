package com.example.limitedticketing.integration.purchasewaiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.member.domain.model.Member;
import com.example.limitedticketing.member.domain.repository.MemberRepository;
import com.example.limitedticketing.purchase.application.port.in.PurchaseCommandPort;
import com.example.limitedticketing.purchase.application.port.in.dto.ReservePurchaseInput;
import com.example.limitedticketing.purchase.domain.repository.PurchaseRepository;
import com.example.limitedticketing.purchasewaiting.application.port.in.worker.PurchaseWaitingWorkerPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.PurchaseWaitingOutboxPort;
import com.example.limitedticketing.purchasewaiting.application.port.out.dto.ReservationOutboxMessage;
import com.example.limitedticketing.purchasewaiting.application.usecase.ClaimPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.ProcessPurchaseWaitingUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.PublishReservationOutboxUseCase;
import com.example.limitedticketing.purchasewaiting.application.usecase.dto.command.ClaimPurchaseWaitingCommand;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingEntry;
import com.example.limitedticketing.purchasewaiting.domain.model.PurchaseWaitingStatus;
import com.example.limitedticketing.purchasewaiting.domain.repository.PurchaseWaitingEntryRepository;
import com.example.limitedticketing.sale.domain.model.Product;
import com.example.limitedticketing.sale.domain.model.SaleProduct;
import com.example.limitedticketing.sale.domain.repository.ProductRepository;
import com.example.limitedticketing.sale.domain.repository.SaleProductRepository;
import com.example.limitedticketing.shared.vo.Money;
import com.example.limitedticketing.shared.vo.SalePeriod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=true",
    "limited-ticketing.kafka.provision-topics=true",
    "limited-ticketing.kafka.replication-factor=1",
    "limited-ticketing.kafka.min-in-sync-replicas=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("실제 Kafka 예약 전달과 중복 및 최종 실패 처리")
class PurchaseWaitingKafkaIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69")
            .asCompatibleSubstituteFor("apache/kafka"));

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleProductRepository saleProductRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private PurchaseWaitingEntryRepository purchaseWaitingEntryRepository;
    @Autowired private ClaimPurchaseWaitingUseCase claimPurchaseWaitingUseCase;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private PurchaseWaitingOutboxPort purchaseWaitingOutboxPort;
    @Autowired private PublishReservationOutboxUseCase publishReservationOutboxUseCase;
    @Autowired private PurchaseWaitingWorkerPort purchaseWaitingWorkerPort;
    @MockitoSpyBean private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private KafkaListenerEndpointRegistry listenerRegistry;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private PurchaseCommandPort purchaseCommandPort;
    @MockitoSpyBean private ProcessPurchaseWaitingUseCase processPurchaseWaitingUseCase;

    @Autowired private PlatformTransactionManager transactionManager;

    private static final String TOPIC = "limited-ticketing.purchasewaiting.reservation.v1";
    private static final String DLT = TOPIC + "-dlt";

    @Test
    @DisplayName("HTTP 접수와 Outbox 발행 후 실제 Consumer가 예약하며 같은 메시지를 재소비해도 재고를 중복 차감하지 않는다")
    void httpToKafkaReservationAndDuplicateDelivery() throws Exception {
        PurchaseWaitingEntry candidate = newWaiting();
        String response = mockMvc.perform(MockMvcRequestBuilders.post(
                "/api/sale-products/{id}/queue-entries", candidate.getSaleProductId())
                .header("X-Member-Id", candidate.getMemberId()).header("Idempotency-Key", candidate.getPurchaseRequestKey()))
            .andExpect(MockMvcResultMatchers.status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        UUID entryId = UUID.fromString(objectMapper.readTree(response).path("data").path("queueEntryId").asText());
        purchaseWaitingWorkerPort.dispatchWaitingEntries();
        assertThat(purchaseWaitingEntryRepository.findById(entryId).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
        String payload = jdbcTemplate.queryForObject("SELECT payload FROM reservation_job_outbox WHERE waiting_entry_id = ?", String.class, entryId);
        publishReservationOutboxUseCase.publishReady();
        awaitStatus(entryId, PurchaseWaitingStatus.RESERVED);
        kafkaTemplate.send(TOPIC, candidate.getSaleProductId().toString(), payload).get(10, TimeUnit.SECONDS);
        awaitCommittedOffsets();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM purchases WHERE sale_product_id = ?", Long.class, candidate.getSaleProductId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT reserved_quantity FROM inventories WHERE sale_product_id = ?", Long.class, candidate.getSaleProductId())).isEqualTo(1L);
        assertThat(purchaseWaitingEntryRepository.findById(entryId).orElseThrow().getExecutionAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("지원하지 않는 메시지 버전은 예약하지 않고 원본을 DLT에 저장한 뒤 offset을 진행한다")
    void unsupportedMessageIsQuarantinedBeforeOffsetCommit() throws Exception {
        String raw = "{\"schemaVersion\":99}";
        try (KafkaConsumer<String, String> consumer = dltConsumer()) {
            kafkaTemplate.send(TOPIC, "1", raw).get(10, TimeUnit.SECONDS);
            Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(consumer.poll(Duration.ofMillis(200)))
                    .anySatisfy(record -> assertThat(record.value()).isEqualTo(raw));
            });
        }
        awaitCommittedOffsets();
    }

    @Test
    @DisplayName("기술 실패 세 번 뒤에는 FAILED와 DLT를 남기고 자동 예약 실행을 멈춘다")
    void threeTechnicalFailuresBecomeDurableFailureAndDlt() throws Exception {
        PurchaseWaitingEntry entry = admitted();
        doThrow(new IllegalStateException("reservation database timeout"))
            .when(processPurchaseWaitingUseCase).process(any());
        try (KafkaConsumer<String, String> consumer = dltConsumer()) {
            publishReservationOutboxUseCase.publishReady();
            awaitStatus(entry.getId(), PurchaseWaitingStatus.FAILED);
            PurchaseWaitingEntry failed = purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow();
            assertThat(failed.getExecutionAttempts()).isEqualTo(3);
            assertThat(failed.getFailureCode()).isEqualTo(PurchaseWaitingEntry.TECHNICAL_FAILURE_CODE);
            assertThat(purchaseRepository.findByMemberIdAndPurchaseRequestKey(entry.getMemberId(), entry.getPurchaseRequestKey())).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT available_quantity FROM inventories WHERE sale_product_id = ?", Long.class, entry.getSaleProductId())).isEqualTo(1L);
            publishReservationOutboxUseCase.publishReady();
            Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(consumer.poll(Duration.ofMillis(200)))
                    .anySatisfy(record -> {
                        assertThat(record.value()).contains(entry.getId().toString());
                        assertThat(record.value()).contains("PurchaseReservationExecutionFailed");
                    });
            });
        }
    }

    @Test
    @DisplayName("실행 오류가 반복돼도 이미 커밋된 구매가 있으면 세 번째 시도에서 기존 결과로 복원한다")
    void committedPurchaseIsReconciledAfterThreeExecutionFailures() {
        PurchaseWaitingEntry entry = admitted();
        Long purchaseId = purchaseCommandPort.reserve(new ReservePurchaseInput(
            entry.getMemberId(), entry.getSaleProductId(), entry.getPurchaseRequestKey())).purchaseId();
        doThrow(new IllegalStateException("worker lost response after purchase commit"))
            .when(processPurchaseWaitingUseCase).process(any());
        publishReservationOutboxUseCase.publishReady();
        awaitStatus(entry.getId(), PurchaseWaitingStatus.RESERVED);
        PurchaseWaitingEntry restored = purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(restored.getPurchaseId()).isEqualTo(purchaseId);
        assertThat(restored.getExecutionAttempts()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM reservation_job_outbox WHERE waiting_entry_id = ? AND event_type = 'PurchaseReservationExecutionFailed'", Long.class, entry.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT reserved_quantity FROM inventories WHERE sale_product_id = ?", Long.class, entry.getSaleProductId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Kafka ACK 뒤 완료 저장이 실패해도 Lease 복구로 재발행하며 구매와 재고 효과는 한 번만 남긴다")
    void brokerAckBeforePublisherRollbackAllowsSafeDuplicatePublish() throws Exception {
        PurchaseWaitingEntry entry = admitted();
        AtomicBoolean completionFailed = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            completionFailed.set(true);
            throw new DataAccessResourceFailureException("publisher database commit failed");
        }).when(purchaseWaitingOutboxPort).markPublished(any(), any(), any());
        publishReservationOutboxUseCase.publishReady();
        awaitStatus(entry.getId(), PurchaseWaitingStatus.RESERVED);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilTrue(completionFailed);
        assertThat(purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), entry.getProcessingToken())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT claim_id FROM reservation_job_outbox WHERE waiting_entry_id = ?", UUID.class, entry.getId())).isNotNull();
        Mockito.doCallRealMethod().when(purchaseWaitingOutboxPort).markPublished(any(), any(), any());
        jdbcTemplate.update("UPDATE reservation_job_outbox SET claim_until = ? WHERE waiting_entry_id = ?", LocalDateTime.now().minusSeconds(1), entry.getId());
        publishReservationOutboxUseCase.publishReady();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(purchaseWaitingOutboxPort.findPublishedAt(entry.getId(), entry.getProcessingToken())).isPresent());
        awaitCommittedOffsets();
        assertThat(jdbcTemplate.queryForObject("SELECT publish_attempts FROM reservation_job_outbox WHERE waiting_entry_id = ?", Integer.class, entry.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM purchases WHERE sale_product_id = ?", Long.class, entry.getSaleProductId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT reserved_quantity FROM inventories WHERE sale_product_id = ?", Long.class, entry.getSaleProductId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("DB 결과 확인이 불가능하면 Consumer를 정지하고 offset을 유지하며 복구 뒤 같은 작업을 완료한다")
    void databaseOutageStopsWithoutAdvancingOffset() throws Exception {
        PurchaseWaitingEntry entry = admitted();
        doThrow(new DataAccessResourceFailureException("database unavailable"))
            .when(purchaseWaitingOutboxPort).containsRequest(any(), any(), any(), any());
        RecordMetadata metadata = sendStoredJob(entry);
        try {
            awaitStoppedConsumer();
            assertOffsetRetained(metadata);
            assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getStatus()).isEqualTo(PurchaseWaitingStatus.DISPATCHED);
            assertThat(purchaseWaitingEntryRepository.findById(entry.getId()).orElseThrow().getExecutionAttempts()).isZero();
        } finally {
            Mockito.doCallRealMethod().when(purchaseWaitingOutboxPort).containsRequest(any(), any(), any(), any());
            listenerRegistry.getListenerContainer("purchasewaiting-reservation").start();
        }
        awaitStatus(entry.getId(), PurchaseWaitingStatus.RESERVED);
        awaitCommittedOffsets();
    }

    @Test
    @DisplayName("DLT 전달이 실패하면 원본 offset을 진행하지 않고 Consumer를 정지하며 복구 후 격리한다")
    void failedDltSendDoesNotLoseTheOriginalRecord() throws Exception {
        Mockito.doAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            if (record.topic().equals(DLT)) {
                return CompletableFuture.failedFuture(new IllegalStateException("DLT unavailable"));
            }
            return invocation.callRealMethod();
        }).when(kafkaTemplate).send(ArgumentMatchers.<ProducerRecord<String, String>>any());
        RecordMetadata metadata = kafkaTemplate.send(TOPIC, "1", "invalid-dlt-boundary").get(10, TimeUnit.SECONDS).getRecordMetadata();
        try {
            awaitStoppedConsumer();
            assertOffsetRetained(metadata);
        } finally {
            Mockito.doCallRealMethod().when(kafkaTemplate).send(ArgumentMatchers.<ProducerRecord<String, String>>any());
            listenerRegistry.getListenerContainer("purchasewaiting-reservation").start();
        }
        awaitCommittedOffsets();
    }

    private RecordMetadata sendStoredJob(PurchaseWaitingEntry entry) throws Exception {
        String payload = jdbcTemplate.queryForObject("SELECT payload FROM reservation_job_outbox WHERE waiting_entry_id = ? AND event_type = 'PurchaseReservationRequested'", String.class, entry.getId());
        return kafkaTemplate.send(TOPIC, entry.getSaleProductId().toString(), payload).get(10, TimeUnit.SECONDS).getRecordMetadata();
    }

    private void awaitStoppedConsumer() {
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(listenerRegistry.getListenerContainer("purchasewaiting-reservation").isRunning()).isFalse());
    }

    private void assertOffsetRetained(RecordMetadata metadata) throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            OffsetAndMetadata committed = admin.listConsumerGroupOffsets("purchasewaiting-reservation-v1")
                .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS)
                .get(new TopicPartition(metadata.topic(), metadata.partition()));
            assertThat(committed == null || committed.offset() <= metadata.offset()).isTrue();
        }
    }

    private PurchaseWaitingEntry admitted() {
        PurchaseWaitingEntry entry = purchaseWaitingEntryRepository.save(newWaiting());
        return claimPurchaseWaitingUseCase.claim(new ClaimPurchaseWaitingCommand(entry.getId(), UUID.randomUUID())).orElseThrow();
    }

    private void awaitStatus(UUID entryId, PurchaseWaitingStatus status) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(purchaseWaitingEntryRepository.findById(entryId).orElseThrow().getStatus()).isEqualTo(status));
    }

    private void awaitCommittedOffsets() {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets("purchasewaiting-reservation-v1").partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
                Set<TopicPartition> partitions = IntStream.range(0, 6)
                    .mapToObj(partition -> new TopicPartition(TOPIC, partition)).collect(Collectors.toSet());
                Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
                partitions.forEach(partition -> latest.put(partition, OffsetSpec.latest()));
                Map<TopicPartition, ListOffsetsResultInfo> ends =
                    admin.listOffsets(latest).all().get(5, TimeUnit.SECONDS);
                ends.forEach((partition, offset) -> {
                    if (offset.offset() > 0) {
                        assertThat(committed).containsKey(partition);
                        assertThat(committed.get(partition).offset()).isEqualTo(offset.offset());
                    }
                });
            });
        }
    }

    private KafkaConsumer<String, String> dltConsumer() {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            "bootstrap.servers", kafka.getBootstrapServers(), "group.id", "dlt-test-" + UUID.randomUUID(),
            "auto.offset.reset", "earliest", "enable.auto.commit", "false"),
            new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(DLT));
        return consumer;
    }

    private PurchaseWaitingEntry newWaiting() {
        Member member = memberRepository.save(Member.register("outbox-" + UUID.randomUUID(), "대기 회원"));
        Product product = productRepository.save(Product.create("Outbox 상품", "예약"));
        LocalDateTime now = LocalDateTime.now();
        SaleProduct sale = SaleProduct.createDraft(product.getId(), Money.of(BigDecimal.TEN), SalePeriod.of(now.minusMinutes(1), now.plusHours(1)));
        sale.open(now);
        sale = saleProductRepository.save(sale);
        inventoryRepository.save(Inventory.initialize(sale.getId(), 1));
        return PurchaseWaitingEntry.enqueue(UUID.randomUUID(), member.getId(), sale.getId(), "key-" + UUID.randomUUID(), 1, now);
    }
}
