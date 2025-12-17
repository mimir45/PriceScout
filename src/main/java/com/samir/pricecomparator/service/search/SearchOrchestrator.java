package com.samir.pricecomparator.service.search;

import com.samir.pricecomparator.dto.OfferDto;
import com.samir.pricecomparator.dto.OfferSearchResponse;
import com.samir.pricecomparator.metrics.SearchMetricsService;
import com.samir.pricecomparator.service.cache.CacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class SearchOrchestrator {

    private final CacheService cacheService;
    private final JpaSearchService jpaSearchService;
    private final ElasticSearchService elasticSearchService;
    private final SearchMetricsService searchMetrics;

    public SearchOrchestrator(CacheService cacheService,
                             JpaSearchService jpaSearchService,
                             SearchMetricsService searchMetrics,
                             @Autowired(required = false) ElasticSearchService elasticSearchService) {
        this.cacheService = cacheService;
        this.jpaSearchService = jpaSearchService;
        this.searchMetrics = searchMetrics;
        this.elasticSearchService = elasticSearchService;
    }

    @CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
    public OfferSearchResponse search(
            String query,
            String condition,
            String color,
            List<String> shopCodes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int limit
    ) {
        Timer.Sample timer = searchMetrics.startSearchTimer();
        log.debug("SearchOrchestrator: Starting search for query='{}'", query);

        var cachedResult = cacheService.getCachedSearch(query, condition, color);
        if (cachedResult.isPresent()) {
            log.info("Cache HIT for query='{}' - returning cached result", query);
            searchMetrics.recordCacheHit();
            searchMetrics.recordSearchRequest("cache", "hit");
            searchMetrics.recordSearchDuration(timer, "cache", "hit");
            return cachedResult.get();
        }
        log.debug("Cache MISS for query='{}'", query);
        searchMetrics.recordCacheMiss();

        List<OfferDto> offers;
        String source;
        if (elasticSearchService != null) {
            log.debug("Using Elasticsearch for search");
            source = "elasticsearch";
            offers = elasticSearchService.search(
                    query, condition, color, shopCodes, minPrice, maxPrice, limit
            );
            log.info("Elasticsearch search SUCCESS for query='{}' - found {} results", query, offers.size());
        } else {
            log.debug("Elasticsearch not available, using JPA search");
            source = "jpa";
            offers = jpaSearchService.search(
                    query, condition, color, shopCodes, minPrice, maxPrice, limit
            );
            log.info("JPA search SUCCESS for query='{}' - found {} results", query, offers.size());
        }

        searchMetrics.recordSearchRequest(source, "miss");
        searchMetrics.recordSearchResults(source, offers.size());
        searchMetrics.recordSearchDuration(timer, source, "miss");

        OfferSearchResponse response = new OfferSearchResponse(
                query,
                (long) offers.size(),
                offers
        );

        try {
            cacheService.cacheSearchResult(query, condition, color, response);
            log.debug("Cached search result for query='{}'", query);
        } catch (Exception e) {
            log.warn("Failed to cache search result: {}", e.getMessage());
        }

        return response;
    }

    private OfferSearchResponse searchFallback(
            String query,
            String condition,
            String color,
            List<String> shopCodes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int limit,
            Exception ex
    ) {
        Timer.Sample timer = searchMetrics.startSearchTimer();
        searchMetrics.recordFallback();

        log.warn("Elasticsearch circuit breaker OPEN - falling back to JPA search. Error: {}", ex.getMessage());

        if (jpaSearchService == null) {
            log.error("JPA search service is not available - cannot fallback");
            return new OfferSearchResponse(query, 0, List.of());
        }

        List<OfferDto> offers = jpaSearchService.search(
                query, condition, color, shopCodes, minPrice, maxPrice, limit
        );

        searchMetrics.recordSearchRequest("jpa_fallback", "miss");
        searchMetrics.recordSearchResults("jpa_fallback", offers.size());
        searchMetrics.recordSearchDuration(timer, "jpa_fallback", "miss");

        log.info("JPA fallback search SUCCESS for query='{}' - found {} results", query, offers.size());

        return new OfferSearchResponse(
                query,
                (long) offers.size(),
                offers
        );
    }
}
