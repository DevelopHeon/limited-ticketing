package com.example.limitedticketing.sale.infrastructure.adapter.out.redis;

import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductDetail;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductPage;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductSummary;
import com.example.limitedticketing.sale.domain.model.SaleStatus;
import com.example.limitedticketing.shared.vo.Money;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SaleProductCacheCodec {
    private final ObjectMapper objectMapper;

    public SaleProductCacheCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encodeDetail(CachedSaleProductDetail result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new DetailValue(1, result.saleProductId(), result.productName(),
            result.benefitDescription(), result.salePrice().getAmount(), result.saleStartsAt(), result.saleEndsAt(), result.status()));
    }

    public CachedSaleProductDetail decodeDetail(String payload, Long saleProductId) throws JsonProcessingException {
        DetailValue value = objectMapper.readValue(payload, DetailValue.class);
        if (value == null || value.schemaVersion() != 1 || !saleProductId.equals(value.saleProductId())
            || value.benefitDescription() == null) {
            throw new IllegalArgumentException("Unsupported or mismatched detail cache");
        }
        validate(value.saleProductId(), value.productName(), value.salePrice(), value.saleStartsAt(), value.saleEndsAt(), value.status());
        return new CachedSaleProductDetail(value.saleProductId(), value.productName(), value.benefitDescription(),
            Money.of(value.salePrice()), value.saleStartsAt(), value.saleEndsAt(), value.status());
    }

    public String encodePage(CachedSaleProductPage result) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new PageValue(1, result.items().stream()
            .map(item -> new SummaryValue(item.saleProductId(), item.productName(), item.salePrice().getAmount(),
                item.saleStartsAt(), item.saleEndsAt(), item.status())).toList(), result.page(), result.size(), result.totalCount()));
    }

    public CachedSaleProductPage decodePage(String payload, int page, int size) throws JsonProcessingException {
        PageValue value = objectMapper.readValue(payload, PageValue.class);
        if (value == null || value.schemaVersion() != 1 || value.page() != page || value.size() != size
            || value.totalCount() < 0 || value.items() == null || value.items().size() > size) {
            throw new IllegalArgumentException("Unsupported or mismatched page cache");
        }
        List<CachedSaleProductSummary> items = value.items().stream().map(item -> {
            if (item == null) {
                throw new IllegalArgumentException("Missing cached page item");
            }
            validate(item.saleProductId(), item.productName(), item.salePrice(), item.saleStartsAt(), item.saleEndsAt(), item.status());
            return new CachedSaleProductSummary(item.saleProductId(), item.productName(), Money.of(item.salePrice()),
                item.saleStartsAt(), item.saleEndsAt(), item.status());
        }).toList();
        return new CachedSaleProductPage(items, page, size, value.totalCount());
    }

    private void validate(Long id, String name, BigDecimal price, LocalDateTime startsAt, LocalDateTime endsAt, SaleStatus status) {
        if (id == null || id <= 0 || name == null || name.isBlank() || price == null || price.signum() < 0
            || startsAt == null || endsAt == null || !startsAt.isBefore(endsAt) || status == null) {
            throw new IllegalArgumentException("Invalid cached sale product");
        }
    }

    private record DetailValue(int schemaVersion, Long saleProductId, String productName, String benefitDescription,
        BigDecimal salePrice, LocalDateTime saleStartsAt, LocalDateTime saleEndsAt, SaleStatus status) { }

    private record SummaryValue(Long saleProductId, String productName, BigDecimal salePrice,
        LocalDateTime saleStartsAt, LocalDateTime saleEndsAt, SaleStatus status) { }

    private record PageValue(int schemaVersion, List<SummaryValue> items, int page, int size, long totalCount) { }
}
