package com.example.limitedticketing.integration.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.limitedticketing.inventory.domain.model.Inventory;
import com.example.limitedticketing.inventory.domain.repository.InventoryRepository;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.sale.presentation.request.ChangeSalePeriodRequest;
import com.example.limitedticketing.sale.presentation.request.CreateSaleProductRequest;
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
class SaleProductManagementIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryRepository inventoryRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Test
    void adminCanManageSaleProductAndPublicCanReadIt() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long saleProductId = createDraft(now.minusMinutes(1), now.plusHours(1));

        Inventory initializedInventory = inventoryRepository.findBySaleProductId(saleProductId).orElseThrow();
        assertThat(initializedInventory.getAvailableQuantity()).isEqualTo(10);

        mockMvc.perform(get("/api/sale-products")
                .param("status", SaleStatus.DRAFT.name())
                .param("sort", "salePrice,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.items[0].saleProductId").value(saleProductId))
            .andExpect(jsonPath("$.data.items[0].status").value(SaleStatus.DRAFT.name()));

        LocalDateTime changedStart = now.minusSeconds(30);
        LocalDateTime changedEnd = now.plusHours(2);
        mockMvc.perform(put("/api/admin/sale-products/{saleProductId}/sale-period", saleProductId)
                .header("X-Admin-Id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeSalePeriodRequest(changedStart, changedEnd))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(SaleStatus.DRAFT.name()));

        mockMvc.perform(post("/api/admin/sale-products/{saleProductId}/open", saleProductId)
                .header("X-Admin-Id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(SaleStatus.OPEN.name()));

        mockMvc.perform(get("/api/sale-products/{saleProductId}", saleProductId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.productName").value("치킨 할인 쿠폰"))
            .andExpect(jsonPath("$.data.salePrice").value(10000))
            .andExpect(jsonPath("$.data.status").value(SaleStatus.OPEN.name()));

        mockMvc.perform(put("/api/admin/sale-products/{saleProductId}/sale-period", saleProductId)
                .header("X-Admin-Id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeSalePeriodRequest(changedStart, changedEnd))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_SALE_STATE"));

        mockMvc.perform(post("/api/admin/sale-products/{saleProductId}/close", saleProductId)
                .header("X-Admin-Id", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(SaleStatus.CLOSED.name()));
    }

    @Test
    void adminEndpointsRequireDevelopmentAdminHeader() throws Exception {
        mockMvc.perform(post("/api/admin/sale-products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSaleProductRequest(
                    "치킨 할인 쿠폰",
                    "1만원 할인",
                    BigDecimal.valueOf(10_000),
                    LocalDateTime.now().minusMinutes(1),
                    LocalDateTime.now().plusHours(1),
                    10
                ))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void pageSizePolicyIsValidatedByTheApplicationQuery() throws Exception {
        mockMvc.perform(get("/api/sale-products")
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SALE"));
    }

    private long createDraft(LocalDateTime saleStartsAt, LocalDateTime saleEndsAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/sale-products")
                .header("X-Admin-Id", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateSaleProductRequest(
                    "치킨 할인 쿠폰",
                    "1만원 할인",
                    BigDecimal.valueOf(10_000),
                    saleStartsAt,
                    saleEndsAt,
                    10
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value(SaleStatus.DRAFT.name()))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("saleProductId").asLong();
    }
}
