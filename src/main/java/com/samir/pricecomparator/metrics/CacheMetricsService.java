package com.samir.pricecomparator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordCacheOperation(String operation, String cacheName, String result) {
        Counter.builder("pricecomparator_cache_operations_total")
            .description("Cache operations")
            .tag("operation", operation)
            .tag("cache_name", cacheName)
            .tag("result", result)
            .register(meterRegistry)
            .increment();
    }

    public void recordCacheEviction(String cacheName, int count) {
        Counter.builder("pricecomparator_cache_evictions_total")
            .description("Cache evictions")
            .tag("cache_name", cacheName)
            .register(meterRegistry)
            .increment(count);
    }
}
