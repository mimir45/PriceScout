package com.samir.pricecomparator.service.scraper;

import com.samir.pricecomparator.dto.NormalizedProduct;
import com.samir.pricecomparator.dto.ScrapedProductDto;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.entity.ScrapingJob;
import com.samir.pricecomparator.repository.ScrapingJobRepository;
import com.samir.pricecomparator.repository.ShopRepository;
import com.samir.pricecomparator.metrics.ScraperMetricsService;
import com.samir.pricecomparator.service.cache.CacheService;
import com.samir.pricecomparator.service.normalization.ProductNormalizationService;
import com.samir.pricecomparator.service.persistence.OfferPersistenceService;
import com.samir.pricecomparator.service.search.ElasticsearchIndexService;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ScraperOrchestrator {

    private final ShopRepository shopRepository;
    private final ShopScraperFactory scraperFactory;
    private final ProductNormalizationService normalizationService;
    private final ScrapingJobRepository scrapingJobRepository;
    private final CacheService cacheService;
    private final OfferPersistenceService persistenceService;
    private final ElasticsearchIndexService elasticsearchIndexService;
    private final ScraperMetricsService scraperMetrics;

    public ScraperOrchestrator(ShopRepository shopRepository,
                              ShopScraperFactory scraperFactory,
                              ProductNormalizationService normalizationService,
                              ScrapingJobRepository scrapingJobRepository,
                              CacheService cacheService,
                              OfferPersistenceService persistenceService,
                              ScraperMetricsService scraperMetrics,
                              @Autowired(required = false) ElasticsearchIndexService elasticsearchIndexService) {
        this.shopRepository = shopRepository;
        this.scraperFactory = scraperFactory;
        this.normalizationService = normalizationService;
        this.scrapingJobRepository = scrapingJobRepository;
        this.cacheService = cacheService;
        this.persistenceService = persistenceService;
        this.scraperMetrics = scraperMetrics;
        this.elasticsearchIndexService = elasticsearchIndexService;
    }

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public void scrapeAllShops() {
        List<Shop> activeShops = shopRepository.findByActiveTrue();
        log.info("Starting scrape for {} active shops", activeShops.size());

        if (activeShops.isEmpty()) {
            log.warn("No active shops found to scrape");
            return;
        }

        List<CompletableFuture<Void>> futures = activeShops.stream()
                .filter(shop -> scraperFactory.hasScraper(shop.getCode()))
                .map(shop -> CompletableFuture.runAsync(() -> scrapeShop(shop), executorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (elasticsearchIndexService != null) {
            try {
                log.info("Starting Elasticsearch indexing...");
                var stats = elasticsearchIndexService.indexAllOffers();
                log.info("Elasticsearch indexing completed: {} indexed, {} failed",
                        stats.getIndexed(), stats.getFailed());
            } catch (Exception e) {
                log.error("Elasticsearch indexing failed (non-fatal): {}", e.getMessage(), e);
            }
        } else {
            log.info("Elasticsearch indexing skipped (service not available)");
        }

        cacheService.invalidateAllCaches();

        log.info("Completed scraping all shops. Cache invalidated.");
    }

    public void scrapeShop(Shop shop) {
        ScrapingJob job = startJob(shop);
        Timer.Sample timer = scraperMetrics.startScraperTimer();
        String shopCode = shop.getCode();

        try {
            log.info("Starting scrape for shop: {}", shopCode);

            AbstractShopScraper scraper = scraperFactory.getScraper(shop);

            List<ScrapedProductDto> scrapedProducts = scraper.scrape();
            job.setProductsFound(scrapedProducts.size());
            scraperMetrics.recordProductsFound(shopCode, scrapedProducts.size());

            if (scrapedProducts.isEmpty()) {
                log.warn("No products scraped from shop: {}", shopCode);
                job.setStatus("SUCCESS");
                scraperMetrics.recordScraperAttempt(shopCode, "SUCCESS");
                scraperMetrics.recordScraperDuration(timer, shopCode, "SUCCESS");
                return;
            }

            List<NormalizedProduct> normalizedProducts = normalizationService.normalize(scrapedProducts);

            var stats = persistenceService.persistOffers(normalizedProducts);
            job.setOffersCreated(stats.getCreated());
            job.setOffersUpdated(stats.getUpdated());

            scraperMetrics.recordProductsCreated(shopCode, stats.getCreated());
            scraperMetrics.recordProductsUpdated(shopCode, stats.getUpdated());

            log.info("Persisted {} products from {}: {} created, {} updated",
                    normalizedProducts.size(), shopCode, stats.getCreated(), stats.getUpdated());

            job.setStatus("SUCCESS");
            scraperMetrics.recordScraperAttempt(shopCode, "SUCCESS");
            scraperMetrics.recordScraperDuration(timer, shopCode, "SUCCESS");

            shop.setLastScrapedAt(LocalDateTime.now());
            shopRepository.save(shop);

        } catch (Exception e) {
            log.error("Scraping failed for shop: {}", shopCode, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            scraperMetrics.recordScraperAttempt(shopCode, "FAILED");
            scraperMetrics.recordScraperDuration(timer, shopCode, "FAILED");
        } finally {
            completeJob(job);
        }
    }

    public void scrapeShopByCode(String shopCode) {
        Shop shop = shopRepository.findByCodeIgnoreCase(shopCode)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found: " + shopCode));

        if (!shop.isActive()) {
            throw new IllegalStateException("Shop is not active: " + shopCode);
        }

        if (!scraperFactory.hasScraper(shopCode)) {
            throw new IllegalStateException("No scraper available for shop: " + shopCode);
        }

        scrapeShop(shop);
    }

    private ScrapingJob startJob(Shop shop) {
        ScrapingJob job = new ScrapingJob();
        job.setShop(shop);
        job.setStatus("STARTED");
        job.setStartedAt(LocalDateTime.now());
        return scrapingJobRepository.save(job);
    }

    private void completeJob(ScrapingJob job) {
        LocalDateTime now = LocalDateTime.now();
        job.setCompletedAt(now);
        job.setDurationSeconds((int) Duration.between(job.getStartedAt(), now).getSeconds());
        scrapingJobRepository.save(job);

        log.info("Scraping job completed for shop {} in {} seconds. Status: {}",
                job.getShop().getCode(), job.getDurationSeconds(), job.getStatus());
    }

    public List<ScrapingJob> getRecentJobs(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return scrapingJobRepository.findByStartedAtAfterOrderByStartedAtDesc(since)
                .stream()
                .limit(limit)
                .toList();
    }
}
