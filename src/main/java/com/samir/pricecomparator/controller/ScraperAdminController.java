package com.samir.pricecomparator.controller;

import com.samir.pricecomparator.entity.ScrapingJob;
import com.samir.pricecomparator.service.cache.CacheService;
import com.samir.pricecomparator.service.normalization.ProductRenormalizationService;
import com.samir.pricecomparator.service.scraper.ScraperOrchestrator;
import com.samir.pricecomparator.service.search.ElasticsearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/scraper")
@Slf4j
public class ScraperAdminController {

    private final ScraperOrchestrator scraperOrchestrator;
    private final CacheService cacheService;
    private final ElasticsearchIndexService elasticsearchIndexService;
    private final ProductRenormalizationService renormalizationService;

    public ScraperAdminController(ScraperOrchestrator scraperOrchestrator,
                                 CacheService cacheService,
                                 ProductRenormalizationService renormalizationService,
                                 @Autowired(required = false) ElasticsearchIndexService elasticsearchIndexService) {
        this.scraperOrchestrator = scraperOrchestrator;
        this.cacheService = cacheService;
        this.renormalizationService = renormalizationService;
        this.elasticsearchIndexService = elasticsearchIndexService;
    }

    @PostMapping("/scrape/all")
    public ResponseEntity<Map<String, String>> scrapeAll() {
        log.info("Manual scrape triggered for all shops");

        try {
            new Thread(scraperOrchestrator::scrapeAllShops).start();

            return ResponseEntity.accepted().body(Map.of(
                    "status", "accepted",
                    "message", "Scraping started for all active shops. Check logs for progress."
            ));
        } catch (Exception e) {
            log.error("Failed to start scraping", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/scrape/{shopCode}")
    public ResponseEntity<Map<String, String>> scrapeShop(@PathVariable String shopCode) {
        log.info("Manual scrape triggered for shop: {}", shopCode);

        try {
            new Thread(() -> {
                try {
                    scraperOrchestrator.scrapeShopByCode(shopCode);
                } catch (Exception e) {
                    log.error("Failed to scrape shop: {}", shopCode, e);
                }
            }).start();

            return ResponseEntity.accepted().body(Map.of(
                    "status", "accepted",
                    "message", "Scraping started for shop: " + shopCode
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to start scraping for shop: {}", shopCode, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<ScrapingJob>> getRecentJobs(
            @RequestParam(defaultValue = "10") int limit) {

        List<ScrapingJob> jobs = scraperOrchestrator.getRecentJobs(limit);
        return ResponseEntity.ok(jobs);
    }


    @GetMapping("/cache/stats")
    public ResponseEntity<CacheService.CacheStats> getCacheStats() {
        return ResponseEntity.ok(cacheService.getStats());
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, String>> invalidateCache() {
        log.info("Manual cache invalidation triggered");

        try {
            cacheService.invalidateAllCaches();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "All caches invalidated"
            ));
        } catch (Exception e) {
            log.error("Failed to invalidate cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "scraper-admin"
        ));
    }

    /**
     * Manually trigger Elasticsearch reindexing
     * POST /api/admin/scraper/elasticsearch/reindex
     */
    @PostMapping("/elasticsearch/reindex")
    public ResponseEntity<Map<String, Object>> reindexElasticsearch() {
        log.info("Manual Elasticsearch reindex triggered");

        if (elasticsearchIndexService == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Elasticsearch is disabled"
            ));
        }

        try {
            var stats = elasticsearchIndexService.indexAllOffers();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "indexed", stats.getIndexed(),
                    "failed", stats.getFailed(),
                    "error", stats.getError() != null ? stats.getError() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to reindex Elasticsearch", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Check Elasticsearch health
     * GET /api/admin/scraper/elasticsearch/health
     */
    @GetMapping("/elasticsearch/health")
    public ResponseEntity<Map<String, Object>> elasticsearchHealth() {
        if (elasticsearchIndexService == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "disabled",
                    "message", "Elasticsearch is not enabled"
            ));
        }

        try {
            boolean healthy = elasticsearchIndexService.isHealthy();
            var stats = elasticsearchIndexService.getIndexStats();

            return ResponseEntity.ok(Map.of(
                    "status", healthy ? "healthy" : "unhealthy",
                    "documentCount", stats.getIndexed(),
                    "error", stats.getError() != null ? stats.getError() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to check Elasticsearch health", e);
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unhealthy",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get Elasticsearch index statistics
     * GET /api/admin/scraper/elasticsearch/stats
     */
    @GetMapping("/elasticsearch/stats")
    public ResponseEntity<Map<String, Object>> elasticsearchStats() {
        if (elasticsearchIndexService == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "disabled"
            ));
        }

        try {
            var stats = elasticsearchIndexService.getIndexStats();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "documentCount", stats.getIndexed(),
                    "error", stats.getError() != null ? stats.getError() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Re-normalize all products with updated BrandModelParser
     * POST /api/admin/scraper/renormalize/all
     */
    @PostMapping("/renormalize/all")
    public ResponseEntity<Map<String, Object>> renormalizeAllProducts() {
        log.info("Manual renormalization triggered for all products");

        try {
            var result = renormalizationService.renormalizeAllProducts();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "total", result.total(),
                    "updated", result.updated(),
                    "unchanged", result.unchanged(),
                    "errors", result.errors()
            ));
        } catch (Exception e) {
            log.error("Failed to renormalize products", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Re-normalize only products with missing brand/model
     * POST /api/admin/scraper/renormalize/missing
     */
    @PostMapping("/renormalize/missing")
    public ResponseEntity<Map<String, Object>> renormalizeMissingProducts() {
        log.info("Manual renormalization triggered for products with missing data");

        try {
            var result = renormalizationService.renormalizeProductsWithMissingData();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "total", result.total(),
                    "updated", result.updated(),
                    "unchanged", result.unchanged(),
                    "errors", result.errors()
            ));
        } catch (Exception e) {
            log.error("Failed to renormalize products", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}
