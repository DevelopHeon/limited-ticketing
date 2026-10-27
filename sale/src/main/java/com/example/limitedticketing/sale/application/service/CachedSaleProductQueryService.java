package com.example.limitedticketing.sale.application.service;

import com.example.limitedticketing.sale.application.port.out.SaleProductReadCachePort;
import com.example.limitedticketing.sale.application.port.out.dto.CacheFillLease;
import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductCacheQuery;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductDetailUseCase;
import com.example.limitedticketing.sale.application.usecase.FindSaleProductsUseCase;
import com.example.limitedticketing.sale.application.usecase.LoadSaleProductDetailUseCase;
import com.example.limitedticketing.sale.application.usecase.LoadSaleProductsUseCase;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductQuery;
import com.example.limitedticketing.sale.application.usecase.dto.query.FindSaleProductsQuery;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductDetailResult;
import com.example.limitedticketing.sale.application.usecase.dto.result.SaleProductPageResult;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CachedSaleProductQueryService implements FindSaleProductDetailUseCase, FindSaleProductsUseCase {
    private final SaleProductReadCachePort saleProductReadCachePort;
    private final LoadSaleProductDetailUseCase loadSaleProductDetailUseCase;
    private final LoadSaleProductsUseCase loadSaleProductsUseCase;
    private final boolean enabled;
    private final long waitTimeoutMillis;
    private final long pollIntervalMillis;

    public CachedSaleProductQueryService(SaleProductReadCachePort saleProductReadCachePort,
        LoadSaleProductDetailUseCase loadSaleProductDetailUseCase, LoadSaleProductsUseCase loadSaleProductsUseCase,
        @Value("${limited-ticketing.sale-cache.enabled:true}") boolean enabled,
        @Value("${limited-ticketing.sale-cache.wait-timeout-ms:200}") long waitTimeoutMillis,
        @Value("${limited-ticketing.sale-cache.poll-interval-ms:25}") long pollIntervalMillis) {
        if (waitTimeoutMillis < 0 || pollIntervalMillis <= 0) {
            throw new IllegalArgumentException("Cache wait must be non-negative and poll interval must be positive");
        }
        this.saleProductReadCachePort = saleProductReadCachePort;
        this.loadSaleProductDetailUseCase = loadSaleProductDetailUseCase;
        this.loadSaleProductsUseCase = loadSaleProductsUseCase;
        this.enabled = enabled;
        this.waitTimeoutMillis = waitTimeoutMillis;
        this.pollIntervalMillis = pollIntervalMillis;
    }

    @Override
    public SaleProductDetailResult findDetail(FindSaleProductQuery query) {
        return SaleProductDetailResult.fromCache(load(() -> saleProductReadCachePort.findDetail(query.saleProductId()),
            () -> loadSaleProductDetailUseCase.loadDetail(query).toCacheValue(),
            (lease, result) -> saleProductReadCachePort.storeDetail(query.saleProductId(), lease, result),
            lease -> saleProductReadCachePort.releaseDetail(query.saleProductId(), lease)));
    }

    @Override
    public SaleProductPageResult findPage(FindSaleProductsQuery query) {
        SaleProductCacheQuery cacheQuery = query.toCacheQuery();
        return SaleProductPageResult.fromCache(load(() -> saleProductReadCachePort.findPage(cacheQuery),
            () -> loadSaleProductsUseCase.loadPage(query).toCacheValue(),
            (lease, result) -> saleProductReadCachePort.storePage(cacheQuery, lease, result),
            lease -> saleProductReadCachePort.releasePage(cacheQuery, lease)));
    }

    private <T> T load(Supplier<CacheLookup<T>> lookup, Supplier<T> loader,
        BiConsumer<CacheFillLease, T> store, Consumer<CacheFillLease> release) {
        if (!enabled) {
            return loader.get();
        }
        long startedAt = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(waitTimeoutMillis);
        while (true) {
            CacheLookup<T> result = lookup.get();
            switch (result.status()) {
                case HIT:
                    return result.value();
                case CLAIMED:
                    try {
                        T loaded = loader.get();
                        store.accept(result.lease(), loaded);
                        return loaded;
                    } finally {
                        release.accept(result.lease());
                    }
                case BYPASS:
                    return loader.get();
                case BUSY:
                    long remaining = timeoutNanos - (System.nanoTime() - startedAt);
                    if (remaining <= 0) {
                        return loader.get();
                    }
                    try {
                        TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(pollIntervalMillis)));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return loader.get();
                    }
                    break;
                default:
                    throw new IllegalStateException("Unsupported cache lookup result");
            }
        }
    }
}
