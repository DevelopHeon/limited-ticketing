package com.example.limitedticketing.sale.infrastructure.metrics;

import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SaleCacheMetrics {
    public enum CacheKind { DETAIL, LIST }
    public enum Operation { READ, STORE, RELEASE, DECODE, INVALIDATE }

    private final MeterRegistry meterRegistry;

    public SaleCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordLookup(CacheKind kind, CacheLookup.Status status) {
        meterRegistry.counter("sale.cache.lookup", "kind", kind.name().toLowerCase(Locale.ROOT),
            "result", status.name().toLowerCase(Locale.ROOT)).increment();
    }

    public void recordWrite(CacheKind kind, boolean stored) {
        meterRegistry.counter("sale.cache.write", "kind", kind.name().toLowerCase(Locale.ROOT),
            "result", stored ? "stored" : "rejected").increment();
    }

    public void recordError(Operation operation) {
        meterRegistry.counter("sale.cache.operations", "operation", operation.name().toLowerCase(Locale.ROOT), "result", "error").increment();
    }

    public void recordInvalidation() {
        meterRegistry.counter("sale.cache.operations", "operation", "invalidate", "result", "success").increment();
    }
}
