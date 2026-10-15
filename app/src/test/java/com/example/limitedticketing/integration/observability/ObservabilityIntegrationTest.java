package com.example.limitedticketing.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureObservability
@DisplayName("관측 지표 접근 정책과 실제 HTTP 계측 검증")
class ObservabilityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("상태 정보는 공개하지만 지표는 관리자에게만 제공하고 다른 관측 경로는 차단한다")
    void onlyAdministratorsCanScrapeMetrics() {
        ResponseEntity<String> health = http.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"").doesNotContain("components", "details");
        assertThat(http.getForEntity("/actuator/prometheus", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request("/actuator/prometheus", "X-Member-Id", "1").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request("/actuator/prometheus", "X-Admin-Id", "admin").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request("/actuator/env", "X-Admin-Id", "admin").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("지표를 추가해도 공개 판매 조회와 구매 및 관리자 API 인증 정책을 유지한다")
    void businessAccessRulesRemainUnchanged() {
        assertThat(http.getForEntity("/api/sale-products", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.postForEntity("/api/sale-products/1/purchases", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/admin/sale-products/1/open", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Member-Id", "1");
        assertThat(http.exchange("/api/admin/sale-products/1/open", HttpMethod.POST,
            new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("성공과 실패한 실제 판매 조회를 상태별로 계측하며 자원 지표와 지연 구간을 노출한다")
    void realHttpOutcomesAndResourcesAreObservable() {
        http.getForEntity("/api/sale-products", String.class);
        http.getForEntity("/api/sale-products/9223372036854775807", String.class);
        String metrics = request("/actuator/prometheus", "X-Admin-Id", "admin").getBody();
        assertThat(metrics).contains("http_server_requests_seconds_bucket", "jvm_memory_used_bytes", "hikaricp_connections");
        assertThat(metrics.lines().anyMatch(line -> line.startsWith("http_server_requests_seconds_count{")
            && line.contains("uri=\"/api/sale-products\"") && line.contains("status=\"200\""))).isTrue();
        assertThat(metrics.lines().anyMatch(line -> line.startsWith("http_server_requests_seconds_count{")
            && line.contains("uri=\"/api/sale-products/{saleProductId}\"") && line.contains("status=\"404\""))).isTrue();
        assertThat(metrics).doesNotContain("memberId=", "requestKey=", "queueEntryId=", "runId=");
    }

    private ResponseEntity<String> request(String path, String header, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(header, value);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
