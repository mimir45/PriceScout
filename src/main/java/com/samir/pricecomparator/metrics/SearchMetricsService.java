package com.samir.pricecomparator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchMetricsService {

    private final MeterRegistry meterRegistry;

    public Timer.Sample startSearchTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordSearchRequest(String source, String cacheStatus) {
        Counter.builder("pricecomparator_search_requests_total")
            .description("Total search requests")
            .tag("source", source)
            .tag("cache_status", cacheStatus)
            .register(meterRegistry)
            .increment();
    }

    public void recordSearchDuration(Timer.Sample sample, String source, String cacheStatus) {
        sample.stop(Timer.builder("pricecomparator_search_duration_seconds")
            .description("Search request duration")
            .tag("source", source)
            .tag("cache_status", cacheStatus)
            .register(meterRegistry));
    }

    public void recordSearchResults(String source, int count) {
        Counter.builder("pricecomparator_search_results_total")
            .description("Number of search results returned")
            .tag("source", source)
            .register(meterRegistry)
            .increment(count);
    }

    public void recordCacheHit() {
        Counter.builder("pricecomparator_search_cache_hits_total")
            .description("Cache hits for search queries")
            .register(meterRegistry)
            .increment();
    }

    public void recordCacheMiss() {
        Counter.builder("pricecomparator_search_cache_misses_total")
            .description("Cache misses for search queries")
            .register(meterRegistry)
            .increment();
    }

    public void recordFallback() {
        Counter.builder("pricecomparator_search_fallback_total")
            .description("Circuit breaker fallbacks to JPA")
            .register(meterRegistry)
            .increment();
    }
}
