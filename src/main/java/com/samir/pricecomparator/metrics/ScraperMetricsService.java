package com.samir.pricecomparator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScraperMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        Gauge.builder("pricecomparator_scraper_active_jobs", activeJobs, AtomicInteger::get)
            .description("Number of currently active scraping jobs")
            .register(meterRegistry);
    }

    public void recordScraperAttempt(String shopCode, String status) {
        Counter.builder("pricecomparator_scraper_attempts_total")
            .description("Total number of scraper attempts")
            .tag("shop", shopCode)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startScraperTimer() {
        activeJobs.incrementAndGet();
        return Timer.start(meterRegistry);
    }

    public void recordScraperDuration(Timer.Sample sample, String shopCode, String status) {
        activeJobs.decrementAndGet();
        sample.stop(Timer.builder("pricecomparator_scraper_duration_seconds")
            .description("Time taken to scrape a shop")
            .tag("shop", shopCode)
            .tag("status", status)
            .register(meterRegistry));
    }

    public void recordProductsFound(String shopCode, int count) {
        Counter.builder("pricecomparator_scraper_products_found_total")
            .description("Total products found during scraping")
            .tag("shop", shopCode)
            .register(meterRegistry)
            .increment(count);
    }

    public void recordProductsCreated(String shopCode, int count) {
        Counter.builder("pricecomparator_scraper_products_created_total")
            .description("Total new products created")
            .tag("shop", shopCode)
            .register(meterRegistry)
            .increment(count);
    }

    public void recordProductsUpdated(String shopCode, int count) {
        Counter.builder("pricecomparator_scraper_products_updated_total")
            .description("Total products updated")
            .tag("shop", shopCode)
            .register(meterRegistry)
            .increment(count);
    }
}
